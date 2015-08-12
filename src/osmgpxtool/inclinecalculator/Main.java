package osmgpxtool.inclinecalculator;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
	private static Options cmdOptions;
	private static CommandLine cmd = null;
	static Logger LOGGER = LoggerFactory.getLogger(Main.class);
	private static Properties props;
	private static Connection dbConnection;

	public static void main(String[] args) throws ClassNotFoundException, IOException {
		props = new Properties();
		try {
			props.load(Main.class.getResourceAsStream("/inclinecalculator.properties"));
		} catch (IOException e) {
			LOGGER.error("could not read Properties file");
			System.exit(-1);
		}
		parseArguments(args, props);
		dbConnection = getDbConnection(props);

		InclineCalculator calc = new InclineCalculator(dbConnection, props);
		calc.init();
		calc.run();
		calc.close();

		
		
		try {
			dbConnection.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	private static void parseArguments(String[] args, Properties props) {
		// read command line arguments
		HelpFormatter helpFormater = new HelpFormatter();
		helpFormater.setWidth(Integer.MAX_VALUE);
		CommandLineParser cmdParser = new BasicParser();
		cmdOptions = new Options();
		setupArgumentOptions();
		// parse arguments
		try {
			cmd = cmdParser.parse(cmdOptions, args);
			if (cmd.hasOption('h')) {
				helpFormater.printHelp("OSM GPX INCLINE CALCULATOR", cmdOptions, true);
				System.exit(0);
			}
			assignArguments(props);
		} catch (ParseException parseException) {
			LOGGER.info(parseException.getMessage());
			helpFormater.printHelp("OSM GPX INCLINE CALCULATOR", cmdOptions);
			System.exit(1);
		}
	}

	private static void assignArguments(Properties props) {
		// database properties
		props.put("dbUser", cmd.getOptionValue("U"));
		props.put("dbPassword", cmd.getOptionValue("PW"));
		props.put("dbName", cmd.getOptionValue("D"));
		if (cmd.getOptionValue("H") != null) {
			props.setProperty("dbHost", cmd.getOptionValue("H"));
		}
		if (cmd.getOptionValue("P") != null) {
			props.setProperty("dbPort", cmd.getOptionValue("P"));
		}

	}

	@SuppressWarnings("static-access")
	private static void setupArgumentOptions() {
		// parse command line arguments
		cmdOptions.addOption(new Option("h", "help", false, "displays help"));
		// database properties
		cmdOptions.addOption(OptionBuilder.isRequired().withLongOpt("database").withDescription("Name of database")
				.hasArg().create("D"));
		cmdOptions.addOption(OptionBuilder.isRequired().withLongOpt("user").withDescription("Name of DB-Username")
				.hasArg().create("U"));
		cmdOptions.addOption(OptionBuilder.isRequired().withLongOpt("password").withDescription("Password of DB-User")
				.hasArg().create("PW"));
		cmdOptions.addOption(OptionBuilder.withLongOpt("host").withDescription("Database host <default:localhost>")
				.hasArg().create("H"));
		cmdOptions.addOption(OptionBuilder.withLongOpt("port").withDescription("Database port <default:5432>").hasArg()
				.create("P"));

	}

	/**
	 * establish database connection
	 * 
	 * @throws ClassNotFoundException
	 */
	private static Connection getDbConnection(Properties props) throws ClassNotFoundException {

		// load driver
		Class.forName("org.postgresql.Driver");
		Connection dbConnection = null;
		String url = "jdbc:postgresql://" + props.getProperty("dbHost") + "/" + props.getProperty("dbName");
		try {

			dbConnection = DriverManager.getConnection(url, props.getProperty("dbUser"),
					props.getProperty("dbPassword"));
			dbConnection.setAutoCommit(true);
		} catch (SQLException ex) {
			LOGGER.error("Could not connect to database");
			ex.printStackTrace();
			System.exit(1);
		}
		return dbConnection;
	}

}
