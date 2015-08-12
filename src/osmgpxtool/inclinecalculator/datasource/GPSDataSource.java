package osmgpxtool.inclinecalculator.datasource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osmgpxtool.inclinecalculator.StreetSegment;
import osmgpxtool.inclinecalculator.gps.GpsTracePart;
import osmgpxtool.inclinecalculator.util.Util;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.io.WKBWriter;
import com.vividsolutions.jts.operation.buffer.BufferParameters;

public class GPSDataSource {
	static Logger LOGGER = LoggerFactory.getLogger(GPSDataSource.class);
	private Properties p;
	private PreparedStatement pst = null;
	private WKBWriter wkbWriter;

	public GPSDataSource(Connection con, Properties p) {
		this.p = p;
		wkbWriter = new WKBWriter(3, true);
		try {
			pst = con.prepareStatement("SELECT g." + p.getProperty("t_PpGpxIdCol") + ", g." + p.getProperty("t_PpGpxTrkIdCol") + ","
					+ p.getProperty("t_PpGpxPartIdCol") + ", ST_ASGEOJSON(ST_INTERSECTION(g."
					+ p.getProperty("t_PpGpxGeomCol") + ",ST_GeomFromEWKB(?))) as " + p.getProperty("t_PpGpxGeomCol")
					+ ",ST_ASGEOJSON(ST_INTERSECTION(g." + p.getProperty("t_PpGpxGeomColSmoothed")
					+ ",ST_GeomFromEWKB(?))) as " + p.getProperty("t_PpGpxGeomColSmoothed") + "  FROM "
					+ p.getProperty("t_mmName") + " sg LEFT JOIN " + p.getProperty("t_PpGpxName") + " g ON sg."
					+ p.getProperty("t_mmGpxIdCol") + " = g." + p.getProperty("t_PpGpxIdCol") + " AND sg."
					+ p.getProperty("t_mmTrkIdCol") + " = g." + p.getProperty("t_PpGpxTrkIdCol") + "  WHERE sg."
					+ p.getProperty("t_mmStreetIdCol") + "=? AND ST_INTERSECTS(g." + p.getProperty("t_PpGpxGeomCol")
					+ ",ST_GeomFromEWKB(?));");
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	public List<GpsTracePart> getClippedTracesWithinBufferOf(StreetSegment s) {
		List<GpsTracePart> partList = new ArrayList<GpsTracePart>();

		try {
			double bufferDis = Double.valueOf(p.getProperty("streetBufferDistance"));
			
			int bufferCap;
			
			if (p.getProperty("bufferCap").equals("CAP_FLAT")){
				bufferCap = BufferParameters.CAP_FLAT;
			}else{
				bufferCap = BufferParameters.CAP_ROUND;
			}
		
			Geometry buffer = s.getGeom().buffer(bufferDis, 5, bufferCap);
			buffer.setSRID(4326);
			pst.setBytes(1, wkbWriter.write(buffer));

			pst.setBytes(2, wkbWriter.write(buffer));
			pst.setInt(3, s.getId());
			pst.setBytes(4, wkbWriter.write(buffer));

			ResultSet rs1 = pst.executeQuery();
			while (rs1.next()) {
				
				int gpsId = rs1.getInt(p.getProperty("t_PpGpxIdCol"));
				int trkId = rs1.getInt(p.getProperty("t_PpGpxTrkIdCol"));
				int partId = rs1.getInt(p.getProperty("t_PpGpxPartIdCol"));
				MultiLineString geom = Util.parseJson(rs1.getString(p.getProperty("t_PpGpxGeomCol")));
				MultiLineString geomSmoothed = Util.parseJson(rs1.getString(p.getProperty("t_PpGpxGeomColSmoothed")));
				partList.add(new GpsTracePart(gpsId, trkId, partId, geom, geomSmoothed));

			}

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return partList;
	}
}
