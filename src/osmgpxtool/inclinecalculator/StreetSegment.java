package osmgpxtool.inclinecalculator;

import java.util.List;
import java.util.Map;

import osmgpxtool.inclinecalculator.datasource.StreetDataSource.Landuse;

import com.vividsolutions.jts.geom.LineString;

public class StreetSegment {
	private int id;
	private Map<String, String> tags;
	private LineString geom;
	private List<LineString> profiles;
	private double orthometricLength;
	private Landuse landuse;

	public double getOrthometricLength() {
		return orthometricLength;
	}

	public void setOrthometricLength(double orthometricLength) {
		this.orthometricLength = orthometricLength;
	}

	public StreetSegment(int id, Map<String, String> tags, LineString geom) {
		super();
		this.id = id;
		this.tags = tags;
		this.geom = geom;
	}

	public int getId() {
		return id;
	}

	public Map<String, String> getTags() {
		return tags;
	}

	public LineString getGeom() {
		return geom;
	}

	public List<LineString> getProfiles() {
		return profiles;
	}

	public void setProfiles(List<LineString> profiles) {
		this.profiles = profiles;
	}

	@Override
	public String toString() {
		return "StreetSegment [id=" + id + "]";
	}

	public void setLanduse(Landuse landuse) {
		this.landuse = landuse;

	}

	public Landuse getLanduse() {
		return this.landuse;

	}

}
