/**
 * Google Analytics Spam Filter Insertion Tool (java version)
 * 
 * Original idea: Simo Ahava (simo.s.ahava@gmail.com)
 * Original source: https://github.com/sahava/spam-filter-tool
 * 
 * Created by: Oleksii Batiuk (oleksii.batiuk@gmail.com)
 */
package ru.javatalks.tools.spam;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.analytics.Analytics;
import com.google.api.services.analytics.AnalyticsScopes;
import com.google.api.services.analytics.model.Account;
import com.google.api.services.analytics.model.Accounts;
import com.google.api.services.analytics.model.Filter;
import com.google.api.services.analytics.model.FilterExpression;
import com.google.api.services.analytics.model.FilterRef;
import com.google.api.services.analytics.model.Profile;
import com.google.api.services.analytics.model.ProfileFilterLink;
import com.google.api.services.analytics.model.ProfileFilterLinks;
import com.google.api.services.analytics.model.Profiles;
import com.google.api.services.analytics.model.Webproperties;
import com.google.api.services.analytics.model.Webproperty;
import com.google.common.collect.Sets;

public class AnalyticsSpamFilterTool {

	public static class FileValidator implements IParameterValidator {

		public void validate(String name, String value)
				throws ParameterException {
			final Path path = Paths.get(value);
			if (!Files.exists(path)) {
				throw new ParameterException("File [" + value
						+ "] does not exists");
			} else if (!Files.isReadable(path)) {
				throw new ParameterException("File [" + value
						+ "] can not be read");
			}
		}

	}

	private static class Console {

		private Options options;

		private Console(Options options) {
			this.options = options;
		}

		public void print(Object action) {
			if (this.options.verbose) {
				System.out.print(action);
			}
		}

		public void println(Object result) {
			if (this.options.verbose) {
				System.out.println(result);
			}
		}
	}

	private static class Options {

		@Parameter(names = { "--update", }, description = "Update exsisting filters")
		private boolean update = false;

		@Parameter(names = { "--create", }, description = "Create missing filters")
		private boolean create = false;

		@Parameter(names = { "--help", "-h" }, help = true, description = "Display usage information")
		private boolean help = false;

		@Parameter(names = { "--prefix" }, description = "Filter name prefix")
		private String prefix = "sa_Spam_filter_#";

		@Parameter(names = { "--account" }, description = "Target Google Analytics account name where filters will be created/updated. (EDIT permission is required)", required = false)
		private String account;

		@Parameter(names = { "--apply" }, description = "Apply filters to specified profiles")
		private boolean apply = false;

		@Parameter(names = { "--profiles" }, description = "Comma separated list of Google Analytics profiels to apply filter to. (or '*' to apply to all profiles)")
		private String profiles;

		@Parameter(names = { "--verbose", "-v" }, description = "Print progress to console")
		private boolean verbose = false;

		@Parameter(names = { "--secret" }, description = "Path to p12 key file", required = true, validateWith = FileValidator.class)
		private String secret;

		@Parameter(names = { "--id" }, description = "Service Account email address", required = true)
		private String id;

		@Parameter(names = { "--application" }, description = "Application name to be used in API requests")
		private String application = DEFAULT_APPLICATION;

		@Parameter(names = { "--filters" }, description = "Path to filters file", validateWith = FileValidator.class, required = true)
		private String filters;
	}

	private static final String FILTER_TYPE = "EXCLUDE";
	private static final String FILTER_FIELD = "CAMPAIGN_SOURCE";
	private static final String FILTER_MATCH_TYPE = "MATCHES";
	private static final String ALL_PROFILES = "*";
	private static final String DEFAULT_APPLICATION = "javatalks.ru-JavaSpamFilerTool/1.0";

	public static void main(String[] args) {

		// Parsing and validating options
		final Options options = new Options();
		final JCommander commander = new JCommander(options, args);
		final Console console = new Console(options);

		if (options.help) {
			// Display help and exit
			commander.usage();
			return;
		}

		try {
			final HttpTransport httpTransport = GoogleNetHttpTransport
					.newTrustedTransport();
			final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

			// Authorization
			console.print("Authorizing... ");
			final Credential credential = authorize(httpTransport, jsonFactory,
					options);
			console.println("Success");

			// Initialize Analytics API
			console.print("Initializing Analytics API... ");
			final Analytics analytics = new Analytics.Builder(httpTransport,
					jsonFactory, credential).setApplicationName(
					options.application).build();
			console.println("Success");

			// Find target account
			console.print("Checking target account [" + options.account
					+ "]... ");
			final Account account = findAccount(analytics, options.account);
			if (account != null) {
				console.println("Account found");
			} else {
				console.println("Account was no found");
				throw new IllegalArgumentException("Account ["
						+ options.account + "] was not found");
			}

			// Preparing target filters data
			console.print("Reading filters data... ");
			final Map<String, String> filtersData = readFiltersData(options);
			console.println(filtersData.size() + " filters found");

			// Processing filters and return list of available filters
			final List<Filter> filters = processFilters(analytics, account,
					options, filtersData, console);

			// Applying filters
			applyFilters(analytics, account, options, filters, console);

		} catch (GoogleJsonResponseException ex) {
			System.err.println("There was a service error: "
					+ ex.getDetails().getCode() + " : "
					+ ex.getDetails().getMessage());
			ex.printStackTrace();
			System.exit(1);
		} catch (Throwable ex) {
			ex.printStackTrace();
			System.exit(2);
		}

	}

	private static void applyFilters(Analytics analytics, Account account,
			Options options, List<Filter> filters, Console console)
			throws Exception {

		if (options.profiles != null) {

			final boolean applyToAll = ALL_PROFILES.equals(options.profiles
					.trim());
			final String[] requestedProfiles = options.profiles.trim().split(
					",");
			final Map<Profile, List<Filter>> filtersToLink = new HashMap<Profile, List<Filter>>();

			if (!applyToAll) {
				console.println("Checking target profiles "
						+ Arrays.toString(requestedProfiles));
			}

			final List<Profile> profiles = findProfiles(analytics, account,
					console, applyToAll, requestedProfiles);

			if (profiles.isEmpty()) {
				if (!applyToAll) {
					throw new IllegalArgumentException("Requested profiles "
							+ Arrays.asList(requestedProfiles)
							+ " were not found");
				} else {
					console.println("Account [" + account.getName()
							+ "] doesn't have any profiles");
				}
			} else {
				if (!applyToAll && options.verbose) {
					for (String requestedProfile : requestedProfiles) {
						console.print(requestedProfile + "... ");
						boolean found = false;
						for (Profile profile : profiles) {
							if (requestedProfile.equals(profile.getName())) {
								found = true;
								console.println("Found");
								break;
							}
						}
						if (!found) {
							console.println("Not found");
						}
					}
				}
			}

			// Processing list of profiles for each property to avoid overuse of
			// writes quota limit. Only missing filters will be linked
			for (Profile profile : profiles) {
				final List<Filter> missingFilters = findMissingFilters(
						analytics, profile, filters, console);
				if (!missingFilters.isEmpty()) {
					filtersToLink.put(profile, missingFilters);
				}
			}

			if (!filtersToLink.isEmpty()) {
				if (options.apply) {
					// Linking missing filters to each profile
					if (applyToAll) {
						console.println("Linking missing filters to ALL profiles for the account ["
								+ account.getName() + "]");
					} else {
						console.println("Linking missing filters to found profiles");

					}
					for (Entry<Profile, List<Filter>> entry : filtersToLink
							.entrySet()) {
						linkFilters(analytics, entry.getKey(),
								entry.getValue(), console);
					}
				} else {
					console.println("Linking filters... Skipped");
				}
			} else {
				console.println("Linking filers... Nothing to do");
			}
		} else {
			console.println("Linking filers... No profiles specified. Nothing to do");
		}
	}

	private static Credential authorize(HttpTransport httpTransport,
			JsonFactory jsonFactory, Options options) throws Exception {
		return new GoogleCredential.Builder()
				.setTransport(httpTransport)
				.setJsonFactory(jsonFactory)
				.setServiceAccountId(options.id)
				.setServiceAccountPrivateKeyFromP12File(
						new File(options.secret))
				.setServiceAccountScopes(
						Sets.newHashSet(AnalyticsScopes.ANALYTICS_EDIT))
				.build();
	}

	private static Account findAccount(Analytics analytics, String accountName)
			throws Exception {
		Accounts accounts = analytics.management().accounts().list().execute();
		for (Account account : accounts.getItems()) {
			if (accountName.equals(account.getName())) {
				return account;
			}
		}
		return null;
	}

	private static List<Filter> findMissingFilters(Analytics analytics,
			Profile profile, List<Filter> filters, Console console)
			throws Exception {
		final List<Filter> missingFilters = new LinkedList<Filter>();
		console.println("Checking for existing links. Profile ["
				+ profile.getName() + "] (" + profile.getWebPropertyId() + ")");

		// Query for all available links for current profile
		final ProfileFilterLinks links = analytics
				.management()
				.profileFilterLinks()
				.list(profile.getAccountId(), profile.getWebPropertyId(),
						profile.getId()).execute();

		// Searching for missing filters
		for (Filter filter : filters) {
			boolean found = false;
			console.print(profile.getName() + " > " + filter.getName() + "... ");
			for (ProfileFilterLink link : links.getItems()) {
				if (filter.getName().equals(link.getFilterRef().getName())) {
					found = true;
					console.println("Found");
					break;
				}
			}
			if (!found) {
				console.println("Not found");
				missingFilters.add(filter);
			}
		}
		return missingFilters;
	}

	private static List<Profile> findProfiles(Analytics analytics,
			Account account, Console console, boolean applyToAll,
			String[] requestedProfiles) throws Exception {
		final List<Profile> foundProfiles = new LinkedList<Profile>();

		// Retrieving list of properties
		final Webproperties webproperties = analytics.management()
				.webproperties().list(account.getId()).execute();

		if (webproperties.getItems().isEmpty()) {
			throw new IllegalArgumentException(
					"No properties found for the account [" + account.getName()
							+ "]");
		}

		for (Webproperty webproperty : webproperties.getItems()) {
			Profiles profiles = analytics.management().profiles()
					.list(account.getId(), webproperty.getId()).execute();
			if (applyToAll) {
				foundProfiles.addAll(profiles.getItems());
			} else {
				for (Profile profile : profiles.getItems()) {
					for (String requestedProfile : requestedProfiles) {
						if (requestedProfile.equals(profile.getName())) {
							foundProfiles.add(profile);
							break;
						}
					}
				}
			}
		}

		return foundProfiles;
	}

	private static void linkFilters(Analytics analytics, Profile profile,
			List<Filter> filters, Console console) throws Exception {
		console.println("Linking filters to profile [" + profile.getName()
				+ "] (" + profile.getWebPropertyId() + ")");
		for (Filter filter : filters) {
			try {
				console.print(profile.getName() + " > " + filter.getName()
						+ "... ");
				analytics
						.management()
						.profileFilterLinks()
						.insert(profile.getAccountId(),
								profile.getWebPropertyId(),
								profile.getId(),
								new ProfileFilterLink()
										.setFilterRef(new FilterRef()
												.setId(filter.getId())))
						.execute();
				console.println("Done");
			} catch (GoogleJsonResponseException ex) {
				System.err.println("There was a service error: "
						+ ex.getDetails().getCode() + " : "
						+ ex.getDetails().getMessage());
			}
		}

	}

	private static List<Filter> processFilters(Analytics analytics,
			Account account, Options options, Map<String, String> filtersData,
			Console console) throws Exception {

		final SortedSet<String> filtersToCreate = new TreeSet<String>();
		final List<Filter> filtersToUpdate = new LinkedList<Filter>();
		final List<Filter> availableFilters = new LinkedList<Filter>();

		final List<Filter> filters = analytics.management().filters()
				.list(account.getId()).execute().getItems();

		console.println("Checking for existing filters in account ["
				+ account.getName() + "] ");

		// Processing existing filters to find what's missing
		for (String filterName : filtersData.keySet()) {
			boolean found = false;
			boolean match = true;
			console.print(account.getName() + " > " + filterName + "... ");
			for (Filter filter : filters) {
				if (filterName.equals(filter.getName())) {
					found = true;
					if (!filtersData.get(filterName).equals(
							filter.getExcludeDetails().getExpressionValue())
							|| !FILTER_FIELD.equals(filter.getExcludeDetails()
									.getField())
							|| !FILTER_MATCH_TYPE.equals(filter
									.getExcludeDetails().getMatchType())) {
						match = false;
						filtersToUpdate.add(filter);
						console.println("Doesn't match");
					} else if (found & match) {
						availableFilters.add(filter);
						console.println("Match");
					}
					break;
				}
			}
			if (!found) {
				filtersToCreate.add(filterName);
				console.println("Doesn't exist");
			}
		}

		// Creating filters
		if (!filtersToCreate.isEmpty()) {
			if (options.create) {
				console.println("Creating " + filtersToCreate.size()
						+ " new filters in " + account.getName() + "] account");
				for (String filterName : filtersToCreate) {
					console.print("Creating filter " + account.getName()
							+ " > " + filterName + "... ");
					Filter filter = new Filter();
					filter.setName(filterName);
					filter.setType(FILTER_TYPE);
					FilterExpression filterExpression = new FilterExpression();
					filterExpression.setExpressionValue(filtersData
							.get(filterName));
					filterExpression.setMatchType(FILTER_MATCH_TYPE);
					filterExpression.setCaseSensitive(false);
					filterExpression.setField(FILTER_FIELD);
					filter.setExcludeDetails(filterExpression);
					final Filter createdFilter = analytics.management()
							.filters().insert(account.getId(), filter)
							.execute();
					availableFilters.add(createdFilter);
					console.println("Done");
				}
			} else {
				console.println("Creating new filters... Skipped");
			}
		} else {
			console.println("Creating new filters... Nothing to do");
		}

		// Updating filters
		if (!filtersToUpdate.isEmpty()) {
			if (options.update) {
				console.println("Updating " + filtersToUpdate.size()
						+ " existing filters in " + account.getName()
						+ "] account");
				for (Filter filter : filtersToUpdate) {
					console.print("Updating filter " + account.getName()
							+ " > " + filter.getName() + "... ");
					filter.getExcludeDetails().setExpressionValue(
							filtersData.get(filter.getName()));
					filter.getExcludeDetails().setField(FILTER_FIELD);
					filter.getExcludeDetails().setMatchType(FILTER_MATCH_TYPE);
					Filter updatedFilter = analytics.management().filters()
							.update(account.getId(), filter.getId(), filter)
							.execute();
					availableFilters.add(updatedFilter);
					console.println("Done");
				}
			} else {
				console.println("Updating existing filters... Skipped");
			}
		} else {
			console.println("Updating existing filters... Nothing to do");
		}

		return availableFilters;
	}

	private static Map<String, String> readFiltersData(Options options)
			throws Exception {
		final Map<String, String> filtersData = new TreeMap<String, String>();

		final String filters = new String(Files.readAllBytes(Paths
				.get(options.filters)));

		if (filters.isEmpty()) {
			throw new IllegalArgumentException("Filters file ["
					+ options.filters + "] is empty");
		}

		final String[] lines = filters.replaceAll("(\\r|\\n|')", "").split(",");

		for (int i = 0; i < lines.length; i++) {
			filtersData.put(options.prefix + (i + 1), lines[i]);
		}

		return filtersData;
	}
}