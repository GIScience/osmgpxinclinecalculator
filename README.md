# OsmGpxInclinecalculator
OsmGpxInclinecalculator is a tool to calculate incline information from GPX traces. It requires the GPS data as 3D-Linestrings, a street network and a table relates the GPS traces to the street network (which trace was recorded on which street segment).
Following tools may be used for importing, preprocessing and map matching:
- Importing and filtering by Bbox and elevation attribute: [OsmGpxFilter](https://github.com/GIScience/osmgpxfilter)
- Preprocessing of GPS traces (smoothing of elevation): [OsmGpxPreprocessor](https://github.com/GIScience/osmgpxpreprocessor)
- MapMatching (requires street network): [OsmGpxMapMatcher](https://github.com/GIScience/osmgpxmapmatcher)

The individual names of the database table and the column names can be adjusted in the properties file which is located under resources.

With the tool it is also possible to calculate the incline from a DEM in geotif format (such as SRTM), which is used to evaluate the incline calculated from GPS traces. The path to the geotiff file has to be set in the properties file. If no evaluation is desired just leave the parameter blank or comment it.




### Getting started

1. install maven
2. install git
3. clone project `$ git clone https://github.com/GIScience/osmgpxinclinecalculator`
4. go into project directory `$ cd osmgpxinclinecalculator/`
5. run maven `$ mvn clean package`
6. start application `java -jar target/osmgpxinclinecalculator-0.1.jar <args>`

### Usage
```
 -h,--help              displays help
 
 Requiered Arguments:
 
 -D,--database          Name of databas
 -PW,--password <arg>   Password of DB-User
 -U,--user <arg>        Name of DB-Username
 
 Optional Arguments:

 -H,--host <arg>        Database host <default:localhost>
 -P,--port <arg>        Database port <default:5432>

 


Example java -jar target/osmgpxinclinecalculator-0.1.jar -D gpx_db -U postgres -PW xxx

 ```

### Citation

When using this software for scientific purposes, please cite:

John, S., Hahmann, S., Zipf, A., Bakillah, M., Mobasheri, A., Rousell, A. (2015): [Towards deriving incline values for street networks from voluntarily collected GPS data] (http://koenigstuhl.geog.uni-heidelberg.de/publications/2015/Hahmann/GI_Forum_GPS.pdf). Poster session, GI Forum. Salzburg, Austria.
 
 
 
 ```
 /*|----------------------------------------------------------------------------------------------
 *|														Heidelberg University
 *|	  _____ _____  _____      _                     	Department of Geography		
 *|	 / ____|_   _|/ ____|    (_)                    	Chair of GIScience
 *|	| |  __  | | | (___   ___ _  ___ _ __   ___ ___ 	(C) 2014
 *|	| | |_ | | |  \___ \ / __| |/ _ \ '_ \ / __/ _ \	
 *|	| |__| |_| |_ ____) | (__| |  __/ | | | (_|  __/	Berliner Strasse 48								
 *|	 \_____|_____|_____/ \___|_|\___|_| |_|\___\___|	D-69120 Heidelberg, Germany	
 *|	        	                                       	http://www.giscience.uni-hd.de
 *|								
 *|----------------------------------------------------------------------------------------------*/
 ```
 
