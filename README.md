Overview
========
This example will show several features of Lucene integration with GemFire.

- Creating a simple data region with Lucene indexes using gfsh commands.
- Creating a Lucene index specifying a different analyzer per field
- Storing lucene index into cluster configuration and then used by other members
- Generate data objects and json objects, putting them into gemfire cache with the Lucene indexing creating a co-located region.
- Query using the default StringQueryParser demonstrating standard Lucene syntax.
- Demonstrate using QueryProvider to create a custom lucene query object. The example includes a Range Query for comparing integer values. 
- Start a REST server to show the data region contents
- Start a REST client to run lucene query using function execution
- Demonstrate how to import and use the SoundEx analyzer for more advanced phonetic searches.

This example can be run standalone or by first creating a cluster containing 2 servers, 
one that also feeds data into the region, and a client. Both the server with the feeder 
and the client will do the same lucene queries.

REST URL is:

- http://localhost:8081/gemfire-api/docs/index.html (for feeder)
- http://localhost:8080/gemfire-api/docs/index.html (for server started by gfsh)

To run this example standalone:

cd ./lucene_example
./gradlew run
or
./gradlew run -PappArgs="[1, false]"

To run different parts of this example:
1) Run stand alone server that also generates and feeds data into the region
./gradlew run -PappArgs="[1, false]"

2) Start, stop, then restart the 2nd server to demonstrate recovering index from disk
./gradlew run -PappArgs="[2, true]"

3) Execute Lucene queries from client
./gradlew run -PappArgs="[3]"

4) server with cluster config, need to start a gfsh to create region and index
./gradlew run -PappArgs="[4, true]"


Part-0: preparation

You can use either gemfire or geode
If you are using gemfire 9.1.0:
- download gemfire 9.1.0 from https://network.pivotal.io/products/pivotal-gemfire
- Unzip it to $HOME/pivotal-gemfire-9.1.0

If you are using geode 1.2:
git clone https://git-wip-us.apache.org/repos/asf/geode.git
cd geode
./gradlew build -Dskip.tests=true install

You will need 3 directories with copies of the example code, one for each server and one for the client:

- for server with feeder (may or may not using cluster config)
  create directory $HOME/lucene_demo/server
- for server only
  create directory $HOME/lucene_demo/serveronly
- for client
  create directory $HOME/lucene_demo/client

In each directory do the following steps:

- start a new VM for each directory
- export GEMFIRE=$HOME/pivotal-gemfire-9.1.0
- clone the example code from: 
  git clone https://github.com/dihardman/lucene_example.git
- cd lucene_example
- ./gradlew build

Part-1: create lucene index from scratch in gfsh
================================================

Step 1: start locator, create server
------------------------------------
In any directory...
export GEMFIRE=$HOME/pivotal-gemfire-9.1.0
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

The following demonstrates that persistence perserves both the data region and the Lucene index
Step 5: stop cache server
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


Part-2: Demonstrate Lucene index perserved in cluster configuration
===================================================================

step 1: Start server in gfsh. Create index, region and save into cluster config
------------------------------------------------------------------
In same directory as Part-1...
export GEMFIRE=$HOME/pivotal-gemfire-9.1.0
$GEMFIRE/bin/gfsh

start locator --name=locator1 --port=12345

configure pdx --disk-store=DEFAULT --read-serialized=true

start server --name=server50505 --server-port=50505 --locators=localhost[12345] --start-rest-api --http-service-port=8080 --http-service-bind-address=localhost --group=group50505

gfsh>deploy --jar=<$HOME>/lucene_demo/server/lucene_example/build/libs/lucene_example-0.0.1.jar --group=group50505
  Member    |       Deployed JAR       | Deployed JAR Location
----------- | ------------------------ | -------------------------------------------------------------------------------------
server50505 | lucene_example-0.0.1.jar | /Users/gzhou/lucene_demo/locator/server50505/vf.gf#lucene_example-0.0.1.jar#1


create lucene index --name=analyzerIndex --region=/Person --field=name,email,address,revenue --analyzer=null,org.apache.lucene.analysis.core.KeywordAnalyzer,examples.MyCharacterAnalyzer,null
create lucene index --name=personIndex --region=/Person --field=name,email,address,revenue
create region --name=Person --type=PARTITION_REDUNDANT_PERSISTENT

gfsh>list lucene indexes
 Index Name   | Region Path |                           Indexed Fields                           | Field Analy.. | Status
------------- | ----------- | ------------------------------------------------------------------ | ------------- | -----------
analyzerIndex | /Person     | [revenue, address, name, email]                                    | {revenue=St.. | Initialized
personIndex   | /Person     | [name, email, address, revenue]                                    | {}            | Initialized


step 2: start server with feeder
--------------------------------
In server with feeder VM:
./gradlew run -PappArgs="[4, true]"
Note: This will create the cache and get region and index definition from cluster-configuration stored by the locator.

step 3: do some queries
---------------------
Return to gfsh session in step 1

gfsh>list members
   Name     | Id
----------- | ------------------------------------------------
locator1    | 192.168.1.3(locator1:32892:locator)<ec><v0>:1024
server50505 | 192.168.1.3(server50505:32949)<v1>:1025
server50509 | 192.168.1.3(server50509:33041)<v6>:1026

#analyzerIndex uses imported SoundEx DoubleMetaphone phonetic analyzer for name field

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
Note: KeywordAnalyzer produces more accurate results for email: only found key490. Next query demonstrates this.

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


step 4: Execute Lucene query from client by executing a function on the server
------------------------------------------------------------------------------
In client VM:
cd $HOME/lucene_demo/client/lucene_example
export GEMFIRE=$HOME/pivotal-gemfire-9.1.0
./gradlew run -PappArgs="[3]"

The client calls function "LuceneSearchIndexFunction" on the server and results are returned and displayed on the client.

step 5: view from REST URL
--------------------------
There're 2 REST web servers:

http://localhost:8080/gemfire-api/docs/index.html by gfsh
http://localhost:8084/gemfire-api/docs/index.html by API

There're 3 controllers are prefined:

- functions(or function-access-controller): run a function at server
- region(or pdx-based-crud-controller): view contents of region
- queries(or query-access-controller): runs OQL queries only

step 6: clean up
----------------
On gfsh window: 

gfsh>shutdown --include-locators=true
rm -rf locator1 server50505

On server member which is running at $HOME/lucene_demo/server/lucene_example, 
run ./clean.sh

