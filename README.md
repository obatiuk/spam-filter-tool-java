# spam-filter-tool-java

This is a **Java version** of the original [Spam Filter Insertion Tool](https://github.com/sahava/spam-filter-tool) created by [Simo Ahava](http://www.simoahava.com/).

**Important!**

* This tool uses [Google API Service Account](https://developers.google.com/analytics/devguides/reporting/core/v3/gdataAuthorization#service-accounts) to access Google Analytics data. You **MUST** have access to user management section of the Google Analytics account to add new users.
* Filters patterns file is **not included** in source. You need to create it manually.

## Prerequisites

* Follow [these](https://developers.google.com/analytics/devguides/reporting/core/v3/gdataAuthorization#service-accounts) instructions to create Service Account and corresponding P12 key file (free quota limit is more than enough).
* Add Service Account to Google Analytics Account you will operate on and grand **Edit** permission to it (see [Google Analytics Help](https://support.google.com/analytics/answer/1009702?hl=en&vid=1-635755514993142169-14104921971559985476#Add) for more details).
* Create filters file using the `filters` array content from the [spamfilter.js](https://github.com/sahava/spam-filter-tool/blob/master/js/spamfilter.js) file. Copy array **content** to a new file and remove trailing spaces.

## Source download

```sh
mvn scm:checkout -DcheckoutDirectory=spam-filter-tool-java -DconnectionUrl=scm:git:git://github.com/obatiuk/spam-filter-tool-java.git
```
## Running

```sh
mvn compile exec:java -Dexec.arguments=@<path_to_parameters_file>
```
or

```sh
mvn clean package assembly:single
java -jar target/spam-filter-tool-*-jar-with-dependencies.jar --help
```
### Parameters file examples 

- **Check overall status (no changes):**

	```
	--id
	<service_account_id>@developer.gserviceaccount.com
	--secret
	<path_to_p12_file>
	--account
	<google_analytics_account_name>
	--filters
	<path_to_filters_file>
	--verbose
	--profiles
	*
	```

- **Create/Update filters:**

	```
	--id
	<service_account_id>@developer.gserviceaccount.com
	--secret
	<path_to_p12_file>
	--account
	<google_analytics_account_name>
	--filters
	<path_to_filters_file>
	--verbose
	--create
	--update
	``` 

- **Create/Update filters and apply (e.g.link) them to ALL available profiles:**

	```
	--id
	<service_account_id>@developer.gserviceaccount.com
	--secret
	<path_to_p12_file>
	--account
	<google_analytics_account_name>
	--filters
	<path_to_filters_file>
	--verbose
	--create
	--update
	--apply
	--profiles
	*
	```

Use `--help` argument to see more options
