# JTicket - A Java based open source ticketing system

## Overview
This is an open sourced ticketing system. 
Given my background worked in multiple ticketing company, this project is built completely with my spare time. 
I were trying to leverage latest Java technologies to build it as I found there are quite a lot 
out of dated technologies being used in the ticketing business making it as an old-fashioned technology. 

Ticketing can be with emerging technologies! So it ship with:
* Pure cloud-native and Kubernetes way to package and deployment
* Latest spring boot and spring security technologies 
* In memory SQL database based on Java, distributed if needed 

This project is open-sourced under the license of Apache 2.0 (https://www.apache.org/licenses/). 
Please feel free to raise one issue or join me to contribute it. 

## Usages
This project can be built directly with command as simple as follows
```
mvn clean package
```
or if we wanted to build via Docker
```
docker build
```
Using `java -jar` or `docker run` to start the ticketing system.

The back office UI will be listening at port of %HOST%/8080 and APIs will be available at %HOST/api. 
Specified to API, you can download the swagger spec (OpenAPI v3) via endpoint of /api/swagger.
There are two profiles for development and production, the development profile uses Apache Derby as persistence layer,
whereas the production profile uses Apache Ignite.

Development mode (with Derby as in memory database, swagger enabled, security disabled)
```
java -Dspring.profiles.active=dev -jar jticket-VERSION.jar
```
Production mode (Ignite as in memory database, swagger disabled, security enabled)
```
java -Dspring.profiles.active=production -jar jticket-VERSION.jar
```
As they are both in-memory SQL database, we could feel the power 
of database without adding too much redundant cache and way to keep consistence in application logic. This is the right 
way to use technologies I believe! 

## Authentication
There is build-in mechanism based on crowd in production environment. 
The back-office will therefore require authentication to function, whereas API require HTTP-BASIC auth to be used as well.

## Scaling
There is no complex clustering configuration of scaling out the system as we are leveraging the in-memory database as 
the only sharing points among working nodes. So as long as we can make Ignite cluster running good, JTicket cluster will 
work great accordingly. 

## What's next
Considering the strategy of the open-source project Apache Ignite might move to another approach, we are considering to switch
to another longer term strategy to build our in-memory cache. Now tarantool is becoming a candidate. Whereas it is not JVM 
based implementation some side-car based implementation might needed. 

The current design is to introduce side-car mechanism and deploy the ticketing service and data grid within one Pod, but two containers. 
Key technologies is to have tarantool container becoming headless service (clusterIP: None) in kubernetes. 

* https://github.com/tarantool/cartridge-java
* https://github.com/tarantool/tarantool
* https://hub.docker.com/r/tarantool/tarantool/