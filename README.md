Overview
========
This example will show several features of Lucene integration with GemFire.

- Creating a simple data region with Lucene indexes using gfsh commands.
- Creating a Lucene index specifying a different analyzer per field
- Create lucene index into cluster configuration and then used by other members
- Generate data objects and json objects, putting them into gemfire cache with the Lucene indexing creating a co-located region.
- Query using the default StringQueryParser demonstrating standard Lucene syntax.
- Demonstrate using QueryProvider to create a custom lucene query object. The example will create a Range Query for integer value. 
- Start a REST server to show the data region contents
- Use a REST API (SwaggerUI) to do lucene search by executing a function.
- Start a REST client to run lucene query through function execution
- Demonstrate how to import and use the SoundEx analyzer for more advanced phonetic searches.

It can be run standalone, or in a cluster contains server 
with feeder, server only, client. Both server with feeder 
and client will do the same lucene queries.

The feeding can be done by client too, but testing feed-from-client is not the objective, query-from-client is.

REST URL is:

- http://localhost:8081/gemfire-api/docs/index.html (for feeder)
- http://localhost:8080/gemfire-api/docs/index.html (for server started by gfsh)

The simplest way to run is run a standalone test:

cd ./lucene_example
./gradlew run
or
./gradlew run -PappArgs="[1, false]"

There're following standalone tests:
1) stand alone server with feeder
./gradlew run -PappArgs="[1, false]"

2) server only (with locator), mainly for testing recovery from disk
./gradlew run -PappArgs="[2, true]"

3) client
./gradlew run -PappArgs="[3]"

4) server with cluster config, need to start a gfsh to create region and index
./gradlew run -PappArgs="[4, true]"

5) calculate size: create index and calculate region size
./gradlew run

6) load user data: load a small csv file with 6 records, do a query
./gradlew run -PappArgs="[6, false]"
or
./gradlew run -PappArgs="[6, false, '/Users/gzhou/git3/geode311demo/bin/311-sample.csv']"

Part-0: preparation

You can use either gemfire or geode
If you are using gemfire 9.0.4:
- download gemfire 9.0.4 from https://network.pivotal.io/products/pivotal-gemfire
- Unzip it to $HOME/pivotal-gemfire-9.0.4

If you are using geode 1.2:
git clone https://git-wip-us.apache.org/repos/asf/geode.git
cd geode
./gradlew build -Dskip.tests=true install

Note:
Since the demo used nested object, you have to apply following patch into geode:
diff --git a/geode-assembly/build.gradle b/geode-assembly/build.gradle
index a4f0c69ee..6ab9396f3 100755
--- a/geode-assembly/build.gradle
+++ b/geode-assembly/build.gradle
@@ -176,6 +176,8 @@ def cp = {
         it.contains('lucene-analyzers-common') ||
         it.contains('lucene-core') ||
         it.contains('lucene-queries') ||
+        it.contains('lucene-join') ||
+        it.contains('lucene-grouping') ||
         it.contains('lucene-queryparser')
       }
     }
diff --git a/geode-lucene/build.gradle b/geode-lucene/build.gradle
index 74de7a6a8..360ab55fb 100644
--- a/geode-lucene/build.gradle
+++ b/geode-lucene/build.gradle
@@ -22,6 +22,8 @@ dependencies {
     compile 'org.apache.lucene:lucene-analyzers-common:' + project.'lucene.version'
     compile 'org.apache.lucene:lucene-core:' + project.'lucene.version'
     compile 'org.apache.lucene:lucene-queries:' + project.'lucene.version'
+    compile 'org.apache.lucene:lucene-join:' + project.'lucene.version'
+    compile 'org.apache.lucene:lucene-grouping:' + project.'lucene.version'
     compile ('org.apache.lucene:lucene-queryparser:' + project.'lucene.version') {
       exclude module: 'lucene-sandbox'
     }

You might need 3 copies to run following members:

- server with feeder (may or may not using cluster config)
  location: $HOME/lucene_demo/server/lucene_example
- server only
  location: $HOME/lucene_demo/serveronly/lucene_example
- client
  location: $HOME/lucene_demo/client/lucene_example

Do following steps for each of the 3 copies:

- export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
- Source code can be got from: 
  git clone git@github.com:gesterzhou/lucene_example.git
- cd lucene_example
- ./gradlew build

Part-1: create lucene index from scratch in gfsh
================================================

Step 1: start locator, create server
------------------------------------
cd $HOME/lucene_demo/locator
export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
$GEMFIRE/bin/gfsh

gfsh>start locator --name=locator1 --port=12345

Step 2: start cache server
--------------------------
gfsh>start server --name=server50505 --server-port=50505 --locators=localhost[12345] --start-rest-api --http-service-port=8080 --http-service-bind-address=localhost

Step 3: create lucene index from scratch
----------------------------------------
gfsh>help create lucene index
gfsh>create lucene index --name=testIndex --region=testRegion --field=__REGION_VALUE_FIELD
                 Member                  | Status
---------------------------------------- | ---------------------------------
192.168.1.23(server50505:17200)<v1>:1025 | Successfully created lucene index

gfsh>list lucene indexes --with-stats
Index Name | Region Path |     Indexed Fields     | Field Analy.. | Status  | Query Executions | Updates | Commits | Documents
---------- | ----------- | ---------------------- | ------------- | ------- | ---------------- | ------- | ------- | ---------
testIndex  | /testRegion | [__REGION_VALUE_FIELD] | {__REGION_V.. | Defined | NA               | NA      | NA      | NA

gfsh>create region --name=testRegion --type=PARTITION_REDUNDANT_PERSISTENT
  Member    | Status
----------- | ---------------------------------------------
server50505 | Region "/testRegion" created on "server50505"

gfsh>list lucene indexes --with-stats
Index Name | Region Path |  Indexed Fields   | Field Analyzer |   Status    | Query Executions | Updates | Commits | Documents
---------- | ----------- | ----------------- | -------------- | ----------- | ---------------- | ------- | ------- | ---------
testIndex  | /testRegion | [__REGION_VALUE.. | {}             | Initialized | 0                | 0       | 0       | 0

Step 4: put 3 entries and do query
----------------------------------
gfsh>put --key=1 --value=value1 --region=testRegion
Result      : true
Key Class   : java.lang.String
Key         : 1
Value Class : java.lang.String
Old Value   : <NULL>

gfsh>put --key=2 --value=value2 --region=testRegion
Result      : true
Key Class   : java.lang.String
Key         : 2
Value Class : java.lang.String
Old Value   : <NULL>

gfsh>put --key=3 --value=value3 --region=testRegion
Result      : true
Key Class   : java.lang.String
Key         : 3
Value Class : java.lang.String
Old Value   : <NULL>

gfsh>help search lucene
gfsh>search lucene --name=testIndex --region=/testRegion --queryStrings=value1 --defaultField=__REGION_VALUE_FIELD
key | value  | score
--- | ------ | ---------
1   | value1 | 0.2876821

gfsh>search lucene --name=testIndex --region=/testRegion --queryStrings=value* --defaultField=__REGION_VALUE_FIELD
key | value  | score
--- | ------ | -----
3   | value3 | 1
2   | value2 | 1
1   | value1 | 1

Step 5: view the region in REST 
-------------------------------
http://localhost:8080/gemfire-api/docs/index.html

Step 6: stop cache server
-------------------------
gfsh>stop server --name=server50505

Step 7: start the server again and recover from disk
----------------------------------------------------
gfsh>start server --name=server50505 --server-port=50505 --locators=localhost[12345] --start-rest-api --http-service-port=8080 --http-service-bind-address=localhost
gfsh>list lucene indexes --with-stats
Index Name | Region Path |  Indexed Fields   | Field Analyzer |   Status    | Query Executions | Updates | Commits | Documents
---------- | ----------- | ----------------- | -------------- | ----------- | ---------------- | ------- | ------- | ---------
testIndex  | /testRegion | [__REGION_VALUE.. | {}             | Initialized | 0                | 0       | 0       | 0

gfsh>search lucene --name=testIndex --region=/testRegion --queryStrings=value* --defaultField=__REGION_VALUE_FIELD
key | value  | score
--- | ------ | -----
3   | value3 | 1
2   | value2 | 1
1   | value1 | 1

Step 8: clean up
----------------
gfsh>shutdown --include-locators=true
gfsh>exit
rm -rf locator1 server50505

Part-2: A more complex example using gfsh cluster configuration
===============================================================

step 1: Start server in gfsh. Create index, region and save into cluster config
------------------------------------------------------------------
cd $HOME/lucene_demo/locator
export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
$GEMFIRE/bin/gfsh

start locator --name=locator1 --port=12345

configure pdx --disk-store=DEFAULT --read-serialized=true

start server --name=server50505 --server-port=50505 --locators=localhost[12345] --start-rest-api --http-service-port=8080 --http-service-bind-address=localhost --group=group50505

gfsh>deploy --jar=/Users/gzhou/lucene_demo/server/lucene_example/build/libs/lucene_example-0.0.1.jar --group=group50505
  Member    |       Deployed JAR       | Deployed JAR Location
----------- | ------------------------ | -------------------------------------------------------------------------------------
server50505 | lucene_example-0.0.1.jar | /Users/gzhou/lucene_demo/locator/server50505/vf.gf#lucene_example-0.0.1.jar#1


create lucene index --name=analyzerIndex --region=/Person --field=name,email,address,revenue --analyzer=null,org.apache.lucene.analysis.core.KeywordAnalyzer,examples.MyCharacterAnalyzer,null
create lucene index --name=personIndex --region=/Person --field=name,email,address,revenue
create lucene index --name=customerIndex --region=/Customer --field=symbol,revenue,SSN,name,email,address,__REGION_VALUE_FIELD
create lucene index --name=pageIndex --region=/Page --field=id,title,content
create region --name=Person --type=PARTITION_REDUNDANT_PERSISTENT
create region --name=Customer --type=PARTITION_REDUNDANT_PERSISTENT
create region --name=Page --type=PARTITION_REDUNDANT_PERSISTENT

gfsh>list lucene indexes
 Index Name   | Region Path |                           Indexed Fields                           | Field Analy.. | Status
------------- | ----------- | ------------------------------------------------------------------ | ------------- | -----------
analyzerIndex | /Person     | [revenue, address, name, email]                                    | {revenue=St.. | Initialized
customerIndex | /Customer   | [symbol, revenue, SSN, name, email, address, __REGION_VALUE_FIELD] | {}            | Initialized
pageIndex     | /Page       | [id, title, content]                                               | {}            | Initialized
personIndex   | /Person     | [name, email, address, revenue]                                    | {}            | Initialized


step 2: start server with feeder
--------------------------------
cd $HOME/lucene_demo/server/lucene_example
export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
./gradlew run -PappArgs="[4, true]"
Note: It will only create cache and get region and index definition from clusterconfiguration saved in locator.

step 3: do some queries
---------------------
Return to gfsh session in step 1

gfsh>list members
   Name     | Id
----------- | ------------------------------------------------
locator1    | 192.168.1.3(locator1:32892:locator)<ec><v0>:1024
server50505 | 192.168.1.3(server50505:32949)<v1>:1025
server50509 | 192.168.1.3(server50509:33041)<v6>:1026

#analyzerIndex used customized analyzer which will tokenize by '_'
gfsh>search lucene --region=/Person --name=analyzerIndex --defaultField=address --queryStrings="97763"
 key   |                                                   value                                                   | score
------ | --------------------------------------------------------------------------------------------------------- | ---------
key763 | Person{name='Tom763 Zhou', email='tzhou763@example.com', address='763 Lindon St, Portland_OR_97763', re.. | 1.6694657

#compare with standard analyzer in personIndex, which cannot find above entry 
gfsh>search lucene --region=/Person --name=personIndex --defaultField=address --queryStrings="97763"
No results

# query json object
gfsh>search lucene --name=personIndex --region=/Person --defaultField=name --queryStrings="Tom*JSON"
  key    |                                                                   value                                                                    | score
-------- | ------------------------------------------------------------------------------------------------------------------------------------------ | -----
jsondoc2 | PDX[3,__GEMFIRE_JSON]{address=PDX[1,__GEMFIRE_JSON]{city=New York, postalCode=10021, state=NY, streetAddress=21 2nd Street}, age=25, las.. | 1
jsondoc1 | PDX[3,__GEMFIRE_JSON]{address=PDX[1,__GEMFIRE_JSON]{city=New York, postalCode=10021, state=NY, streetAddress=21 2nd Street}, age=25, las.. | 1

# query with limit
gfsh>search lucene --name=personIndex --region=/Person --defaultField=name --queryStrings=Tom3* --limit=5

# composite query condition
gfsh>search lucene --name=personIndex --region=/Person --defaultField=name --queryStrings="Tom36* OR Tom422"

# query using keyword analyzer, analyzerIndex uses KeywordAnalyzer for field "email"
gfsh>search lucene --name=analyzerIndex --region=/Person --defaultField=email --queryStrings="email:tzhou490@example.com"
 key   |                                                         value                                                          | score
------ | ---------------------------------------------------------------------------------------------------------------------- | -------
key490 | Person{name='Tom490 Zhou', email='tzhou490@example.com', address='490 Lindon St, Portland_OR_97490', revenue='490000'} | 1.89712
Note: only found key490.

gfsh>search lucene --name=personIndex --region=/Person --defaultField=email --queryStrings="email:tzhou490@example.com"
 key   |                                                         value                                                          | score
------ | ---------------------------------------------------------------------------------------------------------------------- | -----------
key330 | Person{name='Tom330 Zhou', email='tzhou330@example.com', address='330 Lindon St, Portland_OR_97330', revenue='330000'} | 0.05790569
key70  | Person{name='Tom70 Zhou', email='tzhou70@example.com', address='70 Lindon St, Portland_OR_97070', revenue='70000'}     | 0.05790569
key110 | Person{name='Tom110 Zhou', email='tzhou110@example.com', address='110 Lindon St, Portland_OR_97110', revenue='110000'} | 0.05790569
key73  | Person{name='Tom73 Zhou', email='tzhou73@example.com', address='73 Lindon St, Portland_OR_97073', revenue='73000'}     | 0.05790569
key614 | Person{name='Tom614 Zhou', email='tzhou614@example.com', address='614 Lindon St, Portland_OR_97614', revenue='614000'} | 0.05790569
key413 | Person{name='Tom413 Zhou', email='tzhou413@example.com', address='413 Lindon St, Portland_OR_97413', revenue='413000'} | 0.07806893
key490 | Person{name='Tom490 Zhou', email='tzhou490@example.com', address='490 Lindon St, Portland_OR_97490', revenue='490000'} | 1.7481685

Note: found a lot due to search by "example.com", because personIndex is using standard analyzer for field "email".


step 4: query from client
-------------------------
cd $HOME/lucene_demo/client/lucene_example
export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
./gradlew run -PappArgs="[3]"

step 5: view from REST URL
--------------------------
There're 2 REST web servers:

http://localhost:8080/gemfire-api/docs/index.html by gfsh
http://localhost:8084/gemfire-api/docs/index.html by API

There're 3 controllers are prefined:

- functions(or function-access-controller): run a function at server
- region(or pdx-based-crud-controller): view contents of region
- queries(or query-access-controller): run oql query

step 6: clean up
----------------
On gfsh window: 

gfsh>shutdown --include-locators=true
rm -rf locator1 server50505

On server member which is running at $HOME/lucene_demo/server/lucene_example, 
run ./clean.sh

Part-3: recover from disk
==========================
step 1: start locator 
---------------------
cd $HOME/lucene_demo/locator
export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
$GEMFIRE/bin/gfsh
gfsh>start locator --name=locator1 --port=12345

step 2: start a server with feeder
----------------------------------
cd $HOME/lucene_demo/server/lucene_example
export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
./gradlew run -PappArgs="[1, true]"

step 3: start server only member to recover from disk
-----------------------------------------------------
cd $HOME/lucene_demo/server/lucene_example
export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
./gradlew run -PappArgs="[2, true]"
It will recover from disk for both data and index.

run gfsh command to confirm the data and index are all recovered:

gfsh>search lucene --name=analyzerIndex --region=/Person --defaultField=email --queryStrings="email:tzhou490@example.com"
 key   |                                                         value                                                          | score
------ | ---------------------------------------------------------------------------------------------------------------------- | -------
key490 | Person{name='Tom490 Zhou', email='tzhou490@example.com', address='490 Lindon St, Portland_OR_97490', revenue='490000'} | 1.89712

step 4: start a client
----------------------

cd $HOME/lucene_demo/client/lucene_example
export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
./gradlew run -PappArgs="[3]"

step 5: show index definition including analyzers and how index usage in stats

gfsh>describe lucene index --name=personIndex --region=/Person
Index Name  | Region Path |                 Indexed Fields                 | Field Analyzer |   Status    | Query Executions | Updates | Commits | Documents
----------- | ----------- | ---------------------------------------------- | -------------- | ----------- | ---------------- | ------- | ------- | ---------
personIndex | /Person     | [name, email, address, streetAddress, revenue] | {}             | Initialized | 339              | 1008    | 962     | 1004

gfsh>describe lucene index --name=analyzerIndex --region=/Person
 Index Name   | Region Path |     Indexed Fields     |            Field Analyzer             |   Status    | Query Executions | Updates | Commits | Documents
------------- | ----------- | ---------------------- | ------------------------------------- | ----------- | ---------------- | ------- | ------- | ---------
analyzerIndex | /Person     | [address, name, email] | {address=MyCharacterAnalyzer, email.. | Initialized | 1695             | 1008    | 962     | 1004

step 6: clean up

Part-4: call function from client and REST
==========================================
step 1: start locator 
---------------------
cd $HOME/lucene_demo/locator
export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
$GEMFIRE/bin/gfsh
gfsh>start locator --name=locator1 --port=12345

step 2: start server with feeder
--------------------------------
cd $HOME/lucene_demo/server/lucene_example
export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
./gradlew run -PappArgs="[1, true]"

step 3: run a client
--------------------
cd $HOME/lucene_demo/client/lucene_example
export GEMFIRE=$HOME/pivotal-gemfire-9.0.4
./gradlew run -PappArgs="[3]"

The client will call function "LuceneSearchIndexFunction" at server and display results at client.

