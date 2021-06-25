# PCS Scrapper

## What is pcs-scrapper ❓
This is a [ProCyclingStats](https://www.procyclingstats.com/) (PCS) data scraper. It fetches and parses HTML pages to end up building different model entities that will be serialized and exported.

⚠️ pcs-scrapper currently only supports teams and riders scrapping for a given season.

## Setup ⚙️
The single requirement to run this application is **Java 8**.

Once installed, the app must be built using the included Gradle wrapper:
```shell
./gradlew build
```
This will place a runnable Java jar under **build/libs** directory.

## Usage 📙
The app can be executed from the command line:
```shell
java -jar pcs-scrapper.jar
```

```shell
Value for option --season should be always provided in command line.
Usage: pcs-scrapper options_list
Options: 
    --season, -s -> Season (always required) { Int }
    --cachePath, -c -> Cache path { String }
    --output, -o -> Output file path (always required) { String }
    --format, -f -> Output file format (always required) { Value should be one of [json, sqlite] }
    --skipCache, -sc [false] -> Skip cache 
    --help, -h -> Usage info
```
We can see there are a few arguments that can be passed in:
- **season**: Season year to scrap. 
- **cachePath**: Directory to be used as cache for HTML documents (to avoid fetching PCS every type).
- **output**: Location of the output content.
- **format**: Format of the output file (either json or sqlite).
- **skipCache**: Ignore cache to force the remote fetching.
