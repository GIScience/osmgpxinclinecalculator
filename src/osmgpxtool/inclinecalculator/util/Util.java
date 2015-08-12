package osmgpxtool.inclinecalculator.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.densify.Densifier;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;

/**
 * Utility class for different geometric or non-geometric operations
 *
 */

public class Util {
	static Logger LOGGER = LoggerFactory.getLogger(Util.class);

	/**
	 * Parses an hstore object, retrieved from PostgresQL database to a map. It
	 * return null is the hstore object is null as well.
	 * 
	 * @param hstore
	 * @returns
	 */
	public static Map<String, String> hstoreToMap(Object hstore) {
		if (hstore == null) {
			return null;
		} else {
			Map<String, String> tags = new HashMap<String, String>();

			String[] kvpairs = hstore.toString().split("\", \"");

			for (String kvpair : kvpairs) {
				String key = kvpair.split("=>")[0].replaceAll("\"", "");
				String value = kvpair.split("=>")[1].replaceAll("\"", "");

				tags.put(key, value);

			}
			return tags;
		}
	}

	/**
	 * This method buffers a line in the CRS WGS84 with a buffer distance given
	 * in meters. To create the buffer-geometry, the input linestring is
	 * transformed to google mercator projection. Be aware that the given buffer
	 * distance will be applied to the objekt in Google Mercator Projection and
	 * might differ from the orthometric distance.
	 * 
	 * @param geom
	 * @param buffer_distance
	 * @returns null, if SRID is != EPSG:4326 or linestring is null
	 */
	public static Polygon bufferWGS84WithMeters(LineString geom, double buffer_distance) {
		if (geom == null) {
			return null;
		} else if (geom.getSRID() == 4326) {
			try {
				CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:4326");
				CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:3857");
				MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS);

				Polygon targetGeometry = (Polygon) JTS.transform(geom, transform).buffer(buffer_distance);

				MathTransform transform2 = CRS.findMathTransform(targetCRS, sourceCRS);

				return (Polygon) JTS.transform(targetGeometry, transform2);

			} catch (MismatchedDimensionException e) {
				LOGGER.error("input geometry is not in SRID 4326 or could be transformed");
				e.printStackTrace();
				return null;
			} catch (TransformException e) {
				LOGGER.error("input geometry is not in SRID 4326 or could be transformed");
				e.printStackTrace();
				return null;
			} catch (NoSuchAuthorityCodeException e) {
				LOGGER.error("input geometry is not in SRID 4326 or could be transformed");
				e.printStackTrace();
				return null;
			} catch (FactoryException e) {
				LOGGER.error("input geometry is not in SRID 4326 or could be transformed");
				e.printStackTrace();
				return null;
			}
		} else {
			LOGGER.error("input geometry is not in SRID 4326. SRID = " + geom.getSRID());
			return null;
		}
	}

	/**
	 * This method calculates the orthometric length in meters of a LineString
	 * given in SRID EPSG:4326. LineString must be in WGS84 (EPSG:4326). If no
	 * SRID defined or other SRID defined than EPSG:4326 the method will return
	 * 0. Furthermore 0 is returned, if the LineString is null.
	 * 
	 * @param line
	 * @param calc
	 * @return
	 */
	public static double calculateOrthometricLength(LineString line) {
		double distance = 0;
		if (line != null) {
			if (line.getSRID() == 4326) {
				for (int i = 0; i < line.getCoordinates().length - 1; i++) {
					Coordinate p1 = line.getCoordinates()[i];
					Coordinate p2 = line.getCoordinates()[i + 1];

					distance += calculateOrthometricDistance(p1, p2);
				}
			} else {
				LOGGER.warn("Could not calculate orthometric length because SRID is not \"EPSG:4326\", SRID: "
						+ line.getSRID());
			}
		}
		return (double) Math.round(distance * 100) / 100;
	}

	/**
	 * This method calculates the orthometric length in meters between two
	 * coordinates given in SRID EPSG:4326.
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	public static double calculateOrthometricDistance(Coordinate p1, Coordinate p2) {

		double r = 6371000; // metres
		double lon1 = Math.toRadians(p1.x);
		double lat1 = Math.toRadians(p1.y);
		double lon2 = Math.toRadians(p2.x);
		double lat2 = Math.toRadians(p2.y);
		double delta_lat = lat2 - lat1;
		double delta_lon = lon2 - lon1;

		double a = Math.sin(delta_lat / 2) * Math.sin(delta_lat / 2) + Math.cos(lat1) * Math.cos(lat2)
				* Math.sin(delta_lon / 2) * Math.sin(delta_lon / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

		return r * c;

	}

	/**
	 * This method computes the average heading of a LineString. The heading is
	 * returned in a range between 0 and 180°. returns NaN, if LineString is
	 * null. 
	 * 
	 * @param geom
	 * @param calc
	 * @return
	 */
	public static Double calculateBearing(LineString geom) {
		// compute bearing between the nodes of line segment and calculate the
		// mean
		double meanWeightedBearing = 0;
		double lineLength = calculateOrthometricLength(geom);
		if (geom != null) {

			for (int i = 0; i < geom.getCoordinates().length - 1; i++) {
				Coordinate p1 = geom.getCoordinates()[i];
				Coordinate p2 = geom.getCoordinates()[i + 1];
				double bearing = calculateBearing(p1, p2);
				double distance = calculateOrthometricDistance(p1, p2);
				meanWeightedBearing += bearing * (distance / lineLength);
			}

			return meanWeightedBearing;

		} else {
			return Double.NaN;
		}
	}

	/**
	 * Reference: http://www.movable-type.co.uk/scripts/latlong.html
	 * 
	 * Returns bearing between -180 and 180
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	public static double calculateBearing(Coordinate p1, Coordinate p2) {

		double lon1 = Math.toRadians(p1.x);
		double lat1 = Math.toRadians(p1.y);
		double lon2 = Math.toRadians(p2.x);
		double lat2 = Math.toRadians(p2.y);
		double y = Math.sin(lon2 - lon1) * Math.cos(lat2);
		double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon2 - lon1);
		double brng = Math.atan2(y, x);

		return Math.toDegrees(brng);
	}

	/**
	 * This method computes perpendicular profile lines, crossing each node of a
	 * LineString. The profile line is perpendicular to the segment between node
	 * n and node n+1. It returns null of either length is 0 or the LineString
	 * is null.
	 * 
	 * 
	 * @param length
	 * @param geom
	 * @return
	 */
	public static List<LineString> computesProfileLines(double length, LineString geom) {
		if (length == 0 || geom == null) {
			return null;
		} else {
			GeodeticCalculator calc = null;
			try {
				calc = new GeodeticCalculator(CRS.decode("EPSG:4326"));
			} catch (NoSuchAuthorityCodeException e1) {
				e1.printStackTrace();
			} catch (FactoryException e1) {
				e1.printStackTrace();
			}
			// get nodes of street segment
			Coordinate[] nodes = geom.getCoordinates();
			List<LineString> profiles = new ArrayList<LineString>();

			/*
			 * for each node, create a perpendicular line crossing the node and
			 * being perpendicular to the line between node N and node N+1
			 */

			try {
				for (int i = 0; i < nodes.length; i++) {
					Coordinate node = nodes[i];
					Coordinate next_node;
					// if node is last element
					if (i == (nodes.length - 1)) {
						next_node = nodes[i - 1];
					} else {
						next_node = nodes[i + 1];
					}
					double direction = getDirection(node, next_node);
					Coordinate[] profileNodes = new Coordinate[2];
					// calculate start node of new profile line
					calc.setStartingGeographicPoint(node.x, node.y);

					// ifdirection <-180 oder > +180 add / substract 360°
					double newDirection = direction + 90;
					if (newDirection > 180) {
						calc.setDirection(newDirection - 360, length / 2);
					} else {
						calc.setDirection(newDirection, length / 2);
					}
					profileNodes[0] = new Coordinate(calc.getDestinationPosition().getCoordinate()[1], calc
							.getDestinationPosition().getCoordinate()[0]);
					// calculate end node of new profile line
					// if direction <-180 oder > +180 add / substract 360°
					newDirection = direction - 90;
					if (newDirection < -180) {
						calc.setDirection(newDirection + 360, length / 2);
					} else {
						calc.setDirection(newDirection, length / 2);
					}
					profileNodes[1] = new Coordinate(calc.getDestinationPosition().getCoordinate()[1], calc
							.getDestinationPosition().getCoordinate()[0]);
					GeometryFactory geomF = new GeometryFactory();
					profiles.add(geomF.createLineString(profileNodes));
				}

			} catch (TransformException e) {
				e.printStackTrace();
			}
			return profiles;
		}
	}

	/**
	 * Due to the clipping geometry might be returned as a linestring. in this
	 * case create a multiLineString with one LineString
	 * 
	 * 
	 * @param json
	 * @return
	 */
	public static MultiLineString parseJson(String json) {

		if (json == null) {
			return null;
		}

		JSONObject obj = null;
		MultiLineString multiLine = null;
		List<LineString> lineList;
		GeometryFactory geomF = new GeometryFactory(new PrecisionModel(), 4326);
		try {
			obj = new JSONObject(json);
			String type = obj.getString("type");

			if (type.equals("MultiLineString")) {
				lineList = new ArrayList<LineString>();
				// get lines of MultiLineString
				JSONArray lines = obj.getJSONArray("coordinates");
				for (int i = 0; i < lines.length(); i++) {
					// get points of line
					JSONArray points = lines.getJSONArray(i);
					List<Coordinate> pointList = new ArrayList<Coordinate>();
					for (int a = 0; a < points.length(); a++) {
						// get coordinate
						JSONArray coord = points.getJSONArray(a);
						pointList.add(new Coordinate(coord.getDouble(0), coord.getDouble(1), coord.getDouble(2)));
					}
					lineList.add(geomF.createLineString(pointList.toArray(new Coordinate[pointList.size()])));
				}
				multiLine = geomF.createMultiLineString(lineList.toArray(new LineString[lineList.size()]));
				multiLine.setSRID(4326);
			} else if (type.equals("LineString")) {
				lineList = new ArrayList<LineString>();
				// get lines of MultiLineString
				JSONArray points = obj.getJSONArray("coordinates");
				List<Coordinate> pointList = new ArrayList<Coordinate>();
				for (int i = 0; i < points.length(); i++) {
					// get coordinate
					JSONArray coord = points.getJSONArray(i);
					pointList.add(new Coordinate(coord.getDouble(0), coord.getDouble(1), coord.getDouble(2)));
				}
				lineList.add(geomF.createLineString(pointList.toArray(new Coordinate[pointList.size()])));
			} else {
				// LOGGER.warn("Geometry is no MultiLineString or no LineString.");
				return null;
			}
			multiLine = geomF.createMultiLineString(lineList.toArray(new LineString[lineList.size()]));
			multiLine.setSRID(4326);

		} catch (JSONException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Problem parsing json");
		}

		return multiLine;

	}

	private static double getDirection(Coordinate node, Coordinate next_node) {
		GeodeticCalculator calc = null;
		try {
			calc = new GeodeticCalculator(CRS.decode("EPSG:4326"));
		} catch (NoSuchAuthorityCodeException e) {
			e.printStackTrace();
		} catch (FactoryException e) {
			e.printStackTrace();
		}
		calc.setStartingGeographicPoint(node.x, node.y);
		calc.setDestinationGeographicPoint(next_node.x, next_node.y);
		return calc.getAzimuth();
	}

	public static double calculateMeanOfArrayListDouble(List<Double> dList) {

		double sum = 0;
		for (double x : dList) {
			sum += x;
		}

		return sum / dList.size();
	}

	public static double calculateMeanOfArrayListInteger(List<Integer> iList) {

		int sum = 0;
		for (Integer x : iList) {
			sum += x;
		}

		return sum / iList.size();
	}

	public static LineString densifyLineString(LineString geom, double distanceTolerance) {

		return (LineString) Densifier.densify(geom, distanceTolerance);
	}

	public static Double round(Double a, int decimalPlaces) {
		if (a.equals(Double.NaN)){
			return Double.NaN;
		}else{
			BigDecimal b = new BigDecimal(String.valueOf(a)).setScale(decimalPlaces, BigDecimal.ROUND_HALF_UP);
			return b.doubleValue();
		}

	}

	public static Double calculateMedianOfArrayListInteger(List<Integer> integerList) {
		if (integerList.size() > 1) {

			Collections.sort(integerList);
			double median;
			int middle = ((integerList.size()) / 2);
			if (integerList.size() % 2 == 0) {
				int medianA = integerList.get(middle);
				int medianB = integerList.get(middle - 1);
				median = Double.valueOf((medianA + medianB) / 2);
			} else {
				median = Double.valueOf(integerList.get(middle + 1));
			}
			return median;
		} else {
			return Double.NaN;
		}
	}

	public static int getMinOfArrayListInteger(List<Integer> integerList) {
		Collections.sort(integerList);
		return integerList.get(0);
	}

	public static int getMaxOfArrayListInteger(List<Integer> integerList) {

		Collections.sort(integerList);
		return integerList.get(integerList.size() - 1);
	}

	public static Double calculateStandardDeviation(List<Double> doubleList) {
		if (doubleList.size() > 1) {
			double mean = calculateMeanOfArrayListDouble(doubleList);
			double sumOfSquaredResiduals = 0;
			for (double a : doubleList) {
				double residual = mean - a;
				sumOfSquaredResiduals += Math.pow(residual, 2);
			}
			// n-1 observations are redundant
			return Math.sqrt(sumOfSquaredResiduals / (doubleList.size() - 1));
		} else {
			return Double.NaN;
		}

	}

	public static Double calculateStandardDeviation(List<Double> doubleList, double true_value) {
		if (doubleList.size() > 1) {

			double sumOfSquaredResiduals = 0;
			for (double a : doubleList) {
				double residual = true_value - a;
				sumOfSquaredResiduals += Math.pow(residual, 2);
			}
			// n-1 observations are redundant
			return Math.sqrt(sumOfSquaredResiduals / (doubleList.size() - 1));
		} else {
			return Double.NaN;
		}
	}
}