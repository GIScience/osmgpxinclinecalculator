package osmgpxtool.inclinecalculator.datasource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osmgpxtool.inclinecalculator.StreetSegment;
import osmgpxtool.inclinecalculator.util.Util;

import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;

public class StreetDataSource {
	static Logger LOGGER = LoggerFactory.getLogger(StreetDataSource.class);

	private Connection con;
	private Properties p;
	private ResultSet rs;
	private WKBReader wkbReader;

	public enum Landuse {
		RURAL, URBAN, FORESTED, NO_LANDUSE
	}

	public StreetDataSource(Connection con, Properties p) {
		this.con = con;
		this.p = p;
		wkbReader = new WKBReader();
		retrieveData();

	}

	public boolean hasNext() {
		boolean hasNext = false;
		try {
			hasNext = rs.next();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return hasNext;
	}

	public StreetSegment getCurrentStreet() {
		StreetSegment currentStreet = null;
		try {
			int id = rs.getInt(p.getProperty("t_streetIdCol"));
			Map<String, String> tags = Util.hstoreToMap(rs.getObject(p.getProperty("t_streetTags")));
			LineString line = null;
			try {
				line = (LineString) wkbReader.read(rs.getBytes(p.getProperty("t_streetGeomCol")));
				line.setSRID(4326);
			} catch (ParseException e) {
				LOGGER.error("Could not parse LineString");
				e.printStackTrace();
			}
			if (line != null) {
				currentStreet = new StreetSegment(id, tags, line);
				currentStreet.setLanduse(mapLanduse(currentStreet));
				currentStreet.setOrthometricLength(Util.calculateOrthometricLength(line));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return currentStreet;

	}

	private void retrieveData() {
		Statement s;
		try {
			s = con.createStatement();
			rs = s.executeQuery("SELECT " + p.getProperty("t_streetIdCol") + "," + p.getProperty("t_streetTags")
					+ ",ST_ASBINARY(" + p.getProperty("t_streetGeomCol") + ") as " + p.getProperty("t_streetGeomCol")
					+ " FROM " + p.getProperty("t_streetName") + " ORDER BY " + p.getProperty("t_streetIdCol") + ";");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public int getSize() {
		int count = 0;
		try {
			Statement s1 = con.createStatement();
			ResultSet rs2 = s1.executeQuery("SELECT COUNT(*) AS rowcount FROM " + p.getProperty("t_streetName") + ";");
			rs2.next();
			count = rs2.getInt("rowcount");
			rs2.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return count;

	}

	private Landuse mapLanduse(StreetSegment s) {
		
			String landuse_tag = s.getTags().get("incline_landuse");

			if (landuse_tag != null) {
				switch (landuse_tag) {

				case "forest":
					return Landuse.FORESTED;

				case "commercial":
				case "residential":
				case "industrial":
					return Landuse.URBAN;

				case "farm":
				case "farmland":
				case "allotments":
				case "grass":
					return Landuse.RURAL;

				default:
					return Landuse.NO_LANDUSE;
				}
			} else {
				return Landuse.NO_LANDUSE;
			}

		} 

	}
