![JTicket project icon](src/main/resources/static/img/jticket.png)

# JTicket - A Java-based open source ticketing system

## Overview
Many ticketing businesses still run on quite out-of-date technology stacks, but ticketing can be built with emerging
technologies. JTicket is an open source ticketing system for exploring modern event ticketing architecture with Java.

The project is built from hands-on experience in multiple ticketing companies and maintained in spare time. It covers
the operator back office workflow, including venue layout management, event/session setup, hierarchical pricing, order
monitoring, payment state, and check-in support. The goal of JTicket is to show that ticketing software does not have to
stay tied to old-fashioned technology choices: it can be cloud-native, API-first, security-aware, and still pleasant to
run locally.

Highlights:
* Back-office UI for venue, event, session, pricing, poster, and order management
* OpenAPI-described REST APIs for integration and automation
* Spring Boot and Spring Security with OAuth2/OIDC support
* Derby-backed development mode for fast local testing
* CockroachDB/PostgreSQL-compatible production persistence
* Container-friendly packaging for cloud-native and Kubernetes deployment

This project is open-sourced under the Apache 2.0 license (https://www.apache.org/licenses/). Contributions, issues,
testing feedback, and architecture discussions are welcome.

## Usage
Build the project with Maven Daemon:

```bash
mvnd clean package
```

If `mvnd` is not available, use Maven:

```bash
mvn clean package
```

To build a container image:

```bash
docker build .
```

Start the ticketing system with `java -jar` or `docker run`. The back-office UI is available at
`http://HOST:8080/`, and APIs are available under `http://HOST:8080/api/`.
Back-office pages include venue management, event/session/pricing management, and operator order management. In the
development profile, Swagger UI is available at `/api-doc`, and the OpenAPI document is available at `/api/doc`.
There are two profiles for development and production. The development profile uses Apache Derby for local persistence,
whereas the production profile uses CockroachDB/PostgreSQL-compatible persistence.

Development mode (Derby file-backed database, Swagger enabled, security disabled):

```bash
java -Dspring.profiles.active=dev -jar jticket-VERSION.jar
```

Production mode (CockroachDB/PostgreSQL database, Swagger disabled, security enabled):

```bash
java -Dspring.profiles.active=production -jar jticket-VERSION.jar
```

## How it works (API flow)

### Roles
JTicket uses the following roles:

* **Operator**: Maintains venue layouts, event and session metadata, pricing at venue, area, or seat level, and
  event/session order and check-in status.
* **Customer**: Reviews event information, selects seats, and places orders for one or more tickets.
* **Attendant**: Checks attendance evidence, usually a QR code, before admitting a ticket holder to an event.
* **PaymentAgent**: External payment system that calls JTicket after a successful payment. This role is out of scope
  for the built-in back office.

#### Permissions mapping with OAuth2 scope

| Name          | OAuth2 Scope                                       |
|---------------|----------------------------------------------------|
| Operator      | template:write, event:read, event:write, venue:read, seat:read, order:read:all, order:write:all |
| Customer      | event:read, order:read, order:write                |
| Attendant     | event:read, event:write                            |
| PaymentAgent  | order:pay                                          |

### Ticket provision
![Ticket provision flow](https://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/grantsunny/JTicket/refs/heads/main/uml/provision.plantuml)

Event pricing can be managed at default, area, and seat level. Seat-level pricing overrides area pricing, and area
pricing overrides the event default. Pricing lookup APIs return both the direct price assignment, when one exists, and
the effective resolved price with its source. Seats without an effective price are not sellable until an operator assigns
pricing, but they remain selectable in the pricing UI so pricing can be restored. A price cannot be deleted while it is
assigned to any default, area, or seat pricing rule, and pricing cannot be changed for seats that already have orders.

### Ticket purchase
![Ticket purchase flow](https://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/grantsunny/JTicket/refs/heads/main/uml/order.plantuml)

Orders are associated with an event session. Create at least one session for an event before placing orders. Operators
with `order:read:all` can use the Order Management UI to select an event and session, view matching orders, inspect paid
totals, and drill into area seat occupation and check-in state.

### Ticket check-in
![Ticket check-in flow](https://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/grantsunny/JTicket/refs/heads/main/uml/checkin.plantuml)

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
JTicket can run as a replicated deployment with CockroachDB in a hybrid mode. Durable StatefulSet DB nodes provide
persistent storage, while each JTicket deployment starts with memory-backed DB nodes as sidecars.

This configuration lets JTicket scale horizontally while keeping database access close to the application.

## What's next
1. Customer-facing purchase portal
2. Attendance UI, Android application, or SDK
