package osmgpxtool.inclinecalculator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osmgpxtool.inclinecalculator.datasource.GPSDataSource;
import osmgpxtool.inclinecalculator.datasource.RasterDataSource;
import osmgpxtool.inclinecalculator.datasource.StreetDataSource;
import osmgpxtool.inclinecalculator.gps.GpsTracePart;
import osmgpxtool.inclinecalculator.util.Progress;
import osmgpxtool.inclinecalculator.util.Util;

import com.vividsolutions.jts.densify.Densifier;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;

public class InclineCalculator {

	static Logger LOGGER = LoggerFactory.getLogger(InclineCalculator.class);
	private Connection con;
	private Properties p;
	private PreparedStatement insert;
	private int insertBatchSize = 0;

	public InclineCalculator(Connection dbConnection, Properties props) {
		this.con = dbConnection;
		this.p = props;
	}

	public void init() {

		// check database columns
		checkInputdata();
		// Add columns to street table
		Statement addCol = null;

		try {
			String table = p.getProperty("t_streetName") + "_" + p.getProperty("t_streetInclineSuffix");
			addCol = con.createStatement();
			addCol.addBatch("DROP TABLE IF EXISTS " + table + ";");

			addCol.addBatch("CREATE TABLE "
					+ table
					+ " ( street_id integer NOT NULL, street_length double precision,  nr_of_traces integer, incl_gps_std_dev double precision, incline_gps double precision,  incline_lidar double precision, incline_srtm double precision,  delta_gps_lidar double precision,  delta_gps_srtm double precision,  delta_srtm_lidar double precision,   CONSTRAINT "
					+ table + "_pk PRIMARY KEY (street_id), CONSTRAINT " + table
					+ "_fk FOREIGN KEY (street_id) REFERENCES " + p.getProperty("t_streetName") + " ("
					+ p.getProperty("t_streetIdCol") + ") MATCH SIMPLE ON UPDATE CASCADE ON DELETE CASCADE);");

			addCol.executeBatch();

			// TODO prepare update Statements
			insert = con
					.prepareStatement("INSERT INTO "
							+ table
							+ " (street_id,street_length,nr_of_traces, incl_gps_std_dev, incline_gps,incline_lidar , incline_srtm ,  delta_gps_lidar ,  delta_gps_srtm,  delta_srtm_lidar ) VALUES (?,?,?,?,?,?,?,?,?,?);");

		} catch (SQLException e) {
			e.printStackTrace();
			SQLException e2 = e.getNextException();
			e2.printStackTrace();
			System.exit(1);
		}
	}

	public void run() {
		StreetDataSource streets = new StreetDataSource(con, p);
		GPSDataSource gpsSource = new GPSDataSource(con, p);
		// if not set do not init
		RasterDataSource lidarSource = null;
		if (p.getProperty("pathToLidar") != null) {
			if (!p.getProperty("pathToLidar").equals("")) {
				lidarSource = new RasterDataSource(p.getProperty("pathToLidar"));
			}
		}

		RasterDataSource srtmSource = null;
		if (p.getProperty("pathToSrtm") != null) {
			if (!p.getProperty("pathToSrtm").equals("")) {
				srtmSource = new RasterDataSource(p.getProperty("pathToSrtm"));
			}
		}

		Progress pr = new Progress();
		pr.start(streets.getSize());
		int progressPercentPrinted = -1;
		while (streets.hasNext()) {

			// progress
			pr.increment();
			int currentProgressPercent = (int) (Math.round(pr.getProgressPercent()));
			if (currentProgressPercent % 5 == 0 && currentProgressPercent != progressPercentPrinted) {
				LOGGER.info(pr.getProgressMessage());
				progressPercentPrinted = currentProgressPercent;
			}

			StreetSegment s = streets.getCurrentStreet();

			// continue if street is not completely covered by lidar DTM

			// get corresponding GPS traces
			List<GpsTracePart> traces = gpsSource.getClippedTracesWithinBufferOf(s);

			Set<Integer> gpx_ids = new HashSet<Integer>();
			for (GpsTracePart t : traces) {
				gpx_ids.add(t.getId());
			}

			if (!traces.isEmpty()) {

				Set<Double[]> gpsInclineValues = new HashSet<Double[]>();

				// loop through list
				// calculate deltaHs
				for (GpsTracePart g : traces) {
					gpsInclineValues.addAll(calculateInclineOfTrace(g, s));
				}
				/*
				 * deltaHs might be empty, if smoothed geometry is null or the
				 * bearing is not within the specified threshold
				 */

				if (gpsInclineValues.isEmpty()) {
					continue;
				}
				// the gps incline calculated as be a weighted mean, depending
				// on
				// the linelength
				// LOGGER.info("calculate weighted mean" + s.getId() );
				double weightedIncline = calculateWeightedMeanIncline(gpsInclineValues);
				double weightedStandardDeviation = calculateWeightedStandardDeviation(gpsInclineValues);
				int nr_traces = gpx_ids.size();

				// densify geom
				LineString densifiedStreetGeom = (LineString) Densifier.densify(s.getGeom(),
						Double.valueOf(p.getProperty("streetDensifyDistance")));
				densifiedStreetGeom.setSRID(4326);

				// calculate Incline [%]

				double inclineGps = weightedIncline;

				// if Rasterdatasource is null, set to NaN
				Double inclineLidar = Double.NaN;
				if (lidarSource != null) {
					inclineLidar = calculateRasterDEMIncline(lidarSource, densifiedStreetGeom);
				}
				Double inclineSrtm = Double.NaN;
				if (srtmSource != null) {
					inclineSrtm = calculateRasterDEMIncline(srtmSource, densifiedStreetGeom);
				}

				// add to database
				insertIntoDatabase(s, nr_traces, weightedStandardDeviation, inclineGps, inclineLidar, inclineSrtm);
			}

		}

	}

	/**
	 * http://www.itl.nist.gov/div898/software/dataplot/refman2/ch2/weightsd.pdf
	 * 
	 * @param gpsInclineValues
	 * @return
	 */
	private Double calculateWeightedStandardDeviation(Set<Double[]> gpsInclineValues) {
		int n = gpsInclineValues.size();
		if (n > 1) {
			// TODO: find explanation for it
			double weightedMean = calculateWeightedMeanIncline(gpsInclineValues);
			double sumSquareOfdifference = 0;
			double sumWeights = 0;
			for (Double[] arr : gpsInclineValues) {
				// arr[0] = incline, arr[1] = length
				double weight = arr[1];
				sumWeights += weight;
			}

			for (Double[] arr : gpsInclineValues) {
				// arr[0] = incline, arr[1] = length
				double weight = arr[1];
				sumSquareOfdifference += Math.pow(arr[0] - weightedMean, 2) * weight;
				sumWeights += weight;
			}
			double denominator = ((n - 1) * sumWeights) / n;
			return Math.sqrt(sumSquareOfdifference / denominator);
		} else {
			return Double.NaN;
		}

	}

	private double calculateWeightedMeanIncline(Set<Double[]> gpsInclineValues) {
		// loop through map.
		// calculate sum of all distances
		double sumLength = 0;
		double sumInclinesTimesLength = 0;
		for (Double[] arr : gpsInclineValues) {
			// arr[0] = incline, arr[1] = length
			sumInclinesTimesLength += arr[0] * arr[1];
			sumLength += arr[1];
		}

		return Util.round(sumInclinesTimesLength / sumLength, 2);
	}

	private double calculateRasterDEMIncline(RasterDataSource rasterSource, LineString densifiedStreetGeom) {
		double sumIncline = 0;
		// loop through coordinates of street linestring
		for (int i = 0; i < densifiedStreetGeom.getNumPoints() - 1; i++) {
			Coordinate p1 = densifiedStreetGeom.getCoordinateN(i);
			Coordinate p2 = densifiedStreetGeom.getCoordinateN(i + 1);
			double p1z = rasterSource.getHeightAtCoordinate(p1);
			double p2z = rasterSource.getHeightAtCoordinate(p2);
			double deltaH = p2z - p1z;
			sumIncline += deltaH / Util.calculateOrthometricDistance(p1, p2) * 100;
		}
		double meanIncline = sumIncline / (densifiedStreetGeom.getNumPoints() - 1);

		return (double) Math.round(meanIncline * 100) / 100;
	}

	private Set<Double[]> calculateInclineOfTrace(GpsTracePart g, StreetSegment s) {

		// Map<Double, Double> inclineValues = new HashMap<Double, Double>();
		Set<Double[]> inclineValues = new HashSet<Double[]>();
		MultiLineString geom = null;
		if (p.getProperty("usedGeom").equals("raw")) {
			geom = g.getGeom();
		} else if (p.getProperty("usedGeom").equals("smoothed")) {
			geom = g.getGeomSmoothed();
		} else {
			throw new IllegalArgumentException(
					"Wrong argument in properties file. The key \"usedGeom\" must have value either \"raw\" or \"smoothed\".");
		}
		if (geom != null) {
			double bearingStreet = Util.calculateBearing(s.getGeom());
			// loop through all linestring in Multilinestring
			for (int i = 0; i < geom.getNumGeometries(); i++) {
				LineString l = (LineString) geom.getGeometryN(i);
				double lineLength = Util.calculateOrthometricLength(l);
				// calculate bearing
				double bearingGps = Util.calculateBearing(l);
				// LOGGER.info(g.toString() + " gps bearing: " + bearingGps);
				// calculate delta_H
				double weightedIncline = 0;

				for (int a = 0; a < l.getCoordinates().length - 1; a++) {

					Coordinate p1 = l.getCoordinates()[a];

					Coordinate p2 = l.getCoordinates()[a + 1];

					// calculate delta H
					double deltaH = p2.z - p1.z;
					// todo calculate incline, depending on length
					double dis = Util.calculateOrthometricDistance(p1, p2);
					if (dis > 0.0) {
						double incline = deltaH / dis * 100;
						// TODO dis * 100 / length of line
						weightedIncline += incline * (dis / lineLength);
					}
				}

				// adjust deltaH to bearing of street element
				if (isSameDirection(bearingGps, bearingStreet)) {
					Double[] incline_length = new Double[2];
					incline_length[0] = weightedIncline;
					incline_length[1] = lineLength;
					inclineValues.add(incline_length);
				} else if (isOppositeDirection(bearingGps, bearingStreet)) {
					Double[] incline_length = new Double[2];
					incline_length[0] = weightedIncline * -1;
					incline_length[1] = lineLength;
					inclineValues.add(incline_length);
				} else {
					// LOGGER.warn("bearing not similar: gps bearing: " +
					// bearingGps + " street bearing: " + bearingStreet);
					// both bearing are not similar within a 20° threshold
					// do nothing
				}

			}
		}
		return inclineValues;
	}

	private boolean isOppositeDirection(double bearingGps, double bearingStreet) {
		if (bearingGps < 0) {
			if (isSameDirection(bearingGps + 180, bearingStreet)) {
				return true;
			}
		} else if (bearingStreet < 0) {
			if (isSameDirection(bearingGps, bearingStreet + 180)) {
				return true;
			} else {
				return false;
			}
		}

		return false;
	}

	private boolean isSameDirection(double bearingGps, double bearingStreet) {
		if (Math.abs(bearingGps - bearingStreet) < Double.valueOf(p.getProperty("bearingThreshold"))) {
			return true;
		} else {
			return false;
		}
	}

	private void insertIntoDatabase(StreetSegment s, int nr_traces, Double inclineGpsStandardDeviation,
			double inclineGps, double inclineLidar, double inclineSrtm) {
		double streetLength = s.getOrthometricLength();
		try {
			insert.setInt(1, s.getId());
			insert.setDouble(2, streetLength);
			insert.setInt(3, nr_traces);
			if (!inclineGpsStandardDeviation.isNaN()) {
				insert.setDouble(4, inclineGpsStandardDeviation);
			} else {
				insert.setNull(4, java.sql.Types.DOUBLE);
			}
			insert.setDouble(5, inclineGps);
			insert.setDouble(6, inclineLidar);
			insert.setDouble(7, inclineSrtm);
			insert.setDouble(8, Util.round(inclineGps - inclineLidar, 7));
			insert.setDouble(9, Util.round(inclineGps - inclineSrtm, 7));
			insert.setDouble(10, Util.round(inclineSrtm - inclineLidar, 7));
			insert.addBatch();
			insertBatchSize++;
			if (insertBatchSize == 5000) {
				insert.executeBatch();
				insert.clearBatch();
				insertBatchSize = 0;
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			e.getNextException().printStackTrace();
			System.exit(1);
		}
	}

	public void close() {
		if (insert != null) {
			try {
				insert.executeBatch();
				insert.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				e.getNextException().printStackTrace();
			}

		}

	}

	/**
	 * This method checks, if all table and columns name, given in
	 * matching.properties exist in the database. If not all given names are
	 * found, the program will exit.
	 */
	private void checkInputdata() {

		try {
			Statement s = con.createStatement();

			ResultSet rs = s.executeQuery("SELECT * FROM " + p.getProperty("t_PpGpxName") + " WHERE false");
			try {
				rs.findColumn(p.getProperty("t_PpGpxIdCol"));
				rs.findColumn(p.getProperty("t_PpGpxTrkIdCol"));
				rs.findColumn(p.getProperty("t_PpGpxPartIdCol"));
				rs.findColumn(p.getProperty("t_PpGpxGeomCol"));
				rs.findColumn(p.getProperty("t_PpGpxGeomColSmoothed"));
			} catch (SQLException e) {
				LOGGER.error("Coloumn is missing in gpx table.");
				e.printStackTrace();
				System.exit(1);
			}

			rs = s.executeQuery("SELECT * FROM " + p.getProperty("t_streetName") + " WHERE false");
			try {
				rs.findColumn(p.getProperty("t_streetIdCol"));
				rs.findColumn(p.getProperty("t_streetGeomCol"));

			} catch (SQLException e) {
				LOGGER.error("Coloumn is missing in gpx table.");
				e.printStackTrace();
				System.exit(1);
			}

			rs = s.executeQuery("SELECT * FROM " + p.getProperty("t_mmName") + " WHERE false");
			try {
				rs.findColumn(p.getProperty("t_mmStreetIdCol"));
				rs.findColumn(p.getProperty("t_mmGpxIdCol"));
			} catch (SQLException e) {
				LOGGER.error("Coloumn is missing in gpx table.");
				e.printStackTrace();
				System.exit(1);
			}

			s.close();
		} catch (SQLException e) {
			LOGGER.error("Could not find table.");
			e.printStackTrace();
			System.exit(1);
		}

	}

}
