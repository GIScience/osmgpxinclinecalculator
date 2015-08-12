package osmgpxtool.inclinecalculator.datasource;

import java.io.File;
import java.io.IOException;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.factory.Hints;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.coverage.PointOutsideCoverageException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

public class RasterDataSource {
	private String path;
	GridCoverage2D coverage;
	CoordinateReferenceSystem crs;
	Envelope env;

	public RasterDataSource(String path) {
		this.path = path;
		try {
			loadImage();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void loadImage() throws IOException {
		File file = new File(path);

		GeoTiffReader reader = new GeoTiffReader(file, new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE));
		coverage = (GridCoverage2D) reader.read(null);
		crs = coverage.getCoordinateReferenceSystem();
		env = new ReferencedEnvelope(coverage.getEnvelope2D());

	}

	public Double getHeightAtCoordinate(Coordinate c) {

		try {

			float[] result = (float[]) coverage.evaluate(new DirectPosition2D(crs, c.x, c.y));

			return Double.valueOf(result[0]);
		} catch (PointOutsideCoverageException e) {
			return Double.NaN;
		}
	}

}
