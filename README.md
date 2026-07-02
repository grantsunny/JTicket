# JTicket - A Java based open source ticketing system

## Overview
Many ticketing businesses still run on quite out-of-date technology stacks, but ticketing can be built with emerging
technologies. JTicket is an open source ticketing system for exploring modern event ticketing architecture with Java.

The project is built from hands-on experience in multiple ticketing companies and maintained in spare time. It covers
the operator back office workflow, including venue layout management, event/session setup, hierarchical pricing, order
monitoring, payment state, and check-in support. The goal of JTicket is to show that ticketing software does not have to
stay tied to old-fashioned technology choices: it can be cloud-native, API-first, security-aware, and still pleasant to
run locally.

Highlights:
* Back-office UI for venue, event, session, pricing, and order management
* OpenAPI-described REST APIs for integration and automation
* Spring Boot and Spring Security with OAuth2/OIDC support
* Derby-backed development mode for fast local testing
* CockroachDB/PostgreSQL-compatible production persistence
* Container-friendly packaging for cloud-native and Kubernetes deployment

This project is open-sourced under the Apache 2.0 license (https://www.apache.org/licenses/). Contributions, issues,
testing feedback, and architecture discussions are welcome.

## Usages
This project can be built directly with command as simple as follows
```
mvnd clean package
```
If `mvnd` is not available, use `mvn clean package`.

or if we wanted to build via Docker
```
docker build
```
Using `java -jar` or `docker run` to start the ticketing system.

The back office UI will be listening at port of %HOST%/8080 and APIs will be available at %HOST/api. 
Back-office pages include venue management, event/session/pricing management, and operator order management.
Specified to API, you can download the swagger spec (OpenAPI v3) via endpoint of /api/swagger.
There are two profiles for development and production, the development profile uses Apache Derby as persistence layer,
whereas the production profile uses CockroachDB (compliance with PostgreSQL).

Development mode (Derby as in memory database, swagger enabled, security disabled)
```
java -Dspring.profiles.active=dev -jar jticket-VERSION.jar
```
Production mode (CockroachDB as in memory database, swagger disabled, security enabled)
```
java -Dspring.profiles.active=production -jar jticket-VERSION.jar
```
As they are both in-memory SQL database, we could feel the power 
of database without adding too much redundant cache and way to keep consistence in application logic. This is the right 
way to use technologies I believe! 

## How it works (API flow)

### Roles 
We assume following roles in the context of JTicket. 
* **Operator**: On behalf the event organizer, maintain and design the venue and seat layout, supply the metadata of event and session, define the pricing of a given seat at venue, area or seat level, and monitor event/session orders and check-in status.
* **Customer**: The audience of the event, will check the overview and make seat selection and then place order to buy a ticket for one or more seats. 
* **Attendant**: Could be a human or a gateway equipment. Check the evidence of attendance (mostly a QR code) on a ticket before approving the ticket holder to enter the venue for a event.  
* **PaymentAgent**: A system handles the payment and cash-in from customer, expect to trigger API of JTicket upon a successful payment for a given order. Out of the scope of JTicket. 

#### Permissions mapping with OAuth2 scope

| Name          | OAuth2 Scope                                       |
|---------------|----------------------------------------------------|
| Operator      | template:write, event:read, event:write, venue:read, seat:read, order:read:all, order:write:all |
| Customer      | event:read, order:read, order:write                |
| Attendant     | event:read, event:write                            |
| PaymentAgent  | order:pay                                          |

### Ticket provision
![Provision of event](https://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/grantsunny/JTicket/refs/heads/main/uml/provision.plantuml)

Event pricing can be managed at default, area, and seat level. Seat-level pricing overrides area pricing, and area
pricing overrides the event default. Pricing lookup APIs return both the direct price assignment, when one exists, and
the effective resolved price with its source. Seats without an effective price are not sellable until an operator assigns
pricing, but they remain selectable in the pricing UI so pricing can be restored. A price cannot be deleted while it is
assigned to any default, area, or seat pricing rule, and pricing cannot be changed for seats that already have orders.

### Ticket purchase
![Provision of event](https://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/grantsunny/JTicket/refs/heads/main/uml/order.plantuml)

Orders are associated with an event session. Create at least one session for an event before placing orders. Operators
with `order:read:all` can use the Order Management UI to select an event and session, view matching orders, inspect paid
totals, and drill into area seat occupation and check-in state.

### Ticket checkin
![Provision of event](https://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/grantsunny/JTicket/refs/heads/main/uml/checkin.plantuml)

## Authentication
Production uses standard OAuth 2.0 and OpenID Connect. Any compatible OIDC provider can be used; Auth0 is the
illustrated OIDC provider in this guide.
The back-office signs users in through the authorization-code flow and keeps the authenticated user in an HTTP session.
API clients can authenticate independently with JWT bearer access tokens. Browser requests to the API can use the
same authenticated session as the back-office.

## Configure an OIDC provider

JTicket uses an OIDC provider for both browser login and API access:

* Back-office users sign in through OpenID Connect and use an HTTP session.
* API clients send an OAuth2 JWT access token.

The steps below use Auth0 as the illustrated OIDC provider. Equivalent application, API, user, role, and permission
settings can be configured in another compatible provider, although dashboard names and permission mapping may differ.

### 1. Create an OIDC application

In Auth0, under **Applications > Applications**, create a **Regular Web Application** and add:

```text
http://127.0.0.1:8080/login/oauth2/code/jticket
```

to **Allowed Callback URLs**. Add the equivalent HTTPS URL for each deployed environment.

### 2. Create an API

In Auth0, under **Applications > APIs**, create an API with:

* Identifier: `jticket-auth0-demo`
* Signing algorithm: `RS256`
* **RBAC** enabled
* **Add Permissions in the Access Token** enabled

Add the permissions listed in [Permissions mapping with OAuth2 scope](#permissions-mapping-with-oauth2-scope).
The API identifier is the audience used by JTicket.

### 3. Configure users and roles

In Auth0, create users under **User Management > Users**, create the roles described above, and assign each role its
matching API permissions. Assign roles to the human users who access JTicket through Universal Login.

For `PaymentAgent`, use a **Machine to Machine Application** with the Client Credentials flow and grant only
`order:pay`.

### 4. Configure JTicket

Provide the OIDC issuer, application credentials, and API audience through environment variables:

```bash
export JTICKET_OIDC_ISSUER=https://YOUR_OIDC_PROVIDER/
export JTICKET_OIDC_CLIENT_ID=YOUR_CLIENT_ID
export JTICKET_OIDC_CLIENT_SECRET=YOUR_CLIENT_SECRET
export JTICKET_OIDC_AUDIENCE=YOUR_API_AUDIENCE
```

For the illustrated Auth0 setup, `JTICKET_OIDC_AUDIENCE` must match the Auth0 API identifier.

### 5. Verify both login modes

1. Start JTicket with the `production` profile and open the back-office UI.
2. Sign in through the configured OIDC provider and confirm that the UI and its `/api/**` requests use the same session.
3. Call `/api/**` with a JWT access token whose audience is the JTicket API.
4. Confirm that an API request without a session or token returns `401`.

The `dev` profile remains unauthenticated. Production requires authentication and enforces the documented permissions
on individual endpoints.

## Scaling
JTicket run as a replicated deployment with CockroachDB in a hybrid mode. Durable StatefulSet DB nodes provide 
persistent storage, while JTicket deployment starts with memory-backed DB nodes as side-car. 

By such configuration, JTicket can scale horizontally and work with completely in-memory database! 

## What's next
1. Customer-facing purchase portal
2. The Attendance UI - will it be something like a Android application or SDK? 
