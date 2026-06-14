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
* **Operator**: On behalf the event organizer, maintain and design the venue and seat layout, supply the metadata of event and session, define the pricing of a given seat at venue, area or seat level.  
* **Customer**: The audience of the event, will check the overview and make seat selection and then place order to buy a ticket for one or more seats. 
* **Attendant**: Could be a human or a gateway equipment. Check the evidence of attendance (mostly a QR code) on a ticket before approving the ticket holder to enter the venue for a event.  
* **PaymentAgent**: A system handles the payment and cash-in from customer, expect to trigger API of JTicket upon a successful payment for a given order. Out of the scope of JTicket. 

#### Permissions mapping with OAuth2 scope

| Name          | OAuth2 Scope                                       |
|---------------|----------------------------------------------------|
| Operator      | template:write, event:write, venue:read, seat:read |
| Customer      | event:read, order:write                            |
| Attendant     | event:write                                        |
| PaymentAgent  | order:write                                        |

### Ticket provision
![Provision of event](https://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/grantsunny/JTicket/refs/heads/main/uml/provision.plantuml)

### Ticket purchase
![Provision of event](https://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/grantsunny/JTicket/refs/heads/main/uml/order.plantuml)

### Ticket checkin
![Provision of event](https://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/grantsunny/JTicket/refs/heads/main/uml/checkin.plantuml)

## Authentication
Production uses standard OAuth 2.0 and OpenID Connect with Auth0 as the example provider.
The back-office signs users in through the authorization-code flow and keeps the authenticated user in an HTTP session.
API clients can authenticate independently with JWT bearer access tokens. Browser requests to the API can use the
same authenticated session as the back-office.

## Configure Auth0

JTicket uses Auth0 in two ways:

* The back-office UI uses OpenID Connect authorization-code login. After Auth0 authenticates the user, JTicket creates
  an HTTP session that also authorizes browser requests to `/api/**`.
* External API clients use OAuth2 JWT bearer access tokens. Unauthenticated API requests receive HTTP `401` instead of
  being redirected to the login page.

### Create the back-office application

In the Auth0 Dashboard, go to **Applications > Applications**, create an application, and select
**Regular Web Application**.

Configure the application as follows:

| Setting | Value |
|---------|-------|
| Application type | Regular Web Application |
| Allowed grant type | Authorization Code |
| Token endpoint authentication | Client Secret Basic or Client Secret Post |
| ID token signing algorithm | RS256 |
| OIDC conformant | Enabled |

Add every JTicket callback URL to **Allowed Callback URLs**. The callback follows Spring Security's standard pattern:

```text
{baseUrl}/login/oauth2/code/jticket
```

For example:

```text
http://127.0.0.1:8080/login/oauth2/code/jticket
https://jticket.example.com/login/oauth2/code/jticket
```

Do not use localhost, wildcard, or development callback URLs in a production deployment.

Copy the Auth0 domain, Client ID, and Client Secret into the production configuration. Prefer environment variables or
a deployment secret rather than changing the committed demonstration values:

```bash
export SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_AUTH0_ISSUER_URI=https://YOUR_TENANT.auth0.com/
export SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_JTICKET_CLIENT_ID=YOUR_CLIENT_ID
export SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_JTICKET_CLIENT_SECRET=YOUR_CLIENT_SECRET
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://YOUR_TENANT.auth0.com/
```

The `jticket` registration name is significant because it determines both the login route
`/oauth2/authorization/jticket` and the callback route `/login/oauth2/code/jticket`.

### Configure user accounts

Users are resource owners and are managed by Auth0, not by JTicket. For a simple demonstration, enable an Auth0 database
connection and create users under **User Management > Users**. The application can also use social, enterprise,
Active Directory, SAML, or custom database connections supported by Auth0.

Enable the selected connection for the JTicket Regular Web Application. Human **Operator**, **Customer**, and
**Attendant** users authenticate through Auth0 Universal Login. Their Auth0 session becomes a JTicket HTTP session after
the authorization-code callback.

### Create the JTicket API

In **Applications > APIs**, create an API with these settings:

| Setting | Demonstration value |
|---------|---------------------|
| Name | JTicket API |
| Identifier | `jticket-auth0-demo` |
| Signing algorithm | RS256 |

The API Identifier is the OAuth2 audience. It must be identical in both locations:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          audiences: jticket-auth0-demo

ticket:
  oauth2:
    audience: jticket-auth0-demo
```

For another Auth0 API identifier, override both values:

```bash
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES=YOUR_API_IDENTIFIER
export TICKET_OAUTH2_AUDIENCE=YOUR_API_IDENTIFIER
```

JTicket adds this audience to browser authorization requests and validates it on bearer access tokens.

### Define permissions and roles

Add the following permissions to the JTicket API:

```text
template:write
event:read
event:write
venue:read
seat:read
order:write
```

In the API's **RBAC Settings**:

1. Enable **RBAC**.
2. Enable **Add Permissions in the Access Token**.

The second option adds Auth0's `permissions` claim to access tokens. For bearer-authenticated API requests, JTicket maps
both OAuth2 `scope` values and Auth0 `permissions` values to Spring Security authorities with the `SCOPE_` prefix. For
example, `event:write` becomes `SCOPE_event:write`.

Create the following Auth0 roles and assign the listed API permissions:

| Auth0 role | API permissions |
|------------|-----------------|
| Operator | `template:write`, `event:write`, `venue:read`, `seat:read` |
| Customer | `event:read`, `order:write` |
| Attendant | `event:write` |
| PaymentAgent | `order:write` |

Assign one or more roles to each user under **User Management > Users > Roles**. Auth0 role permissions are additive.

`PaymentAgent` normally represents another system rather than a human user. For production integration, create a
separate **Machine to Machine Application**, authorize it for the JTicket API, and grant only `order:write`. It should
use the Client Credentials flow and call JTicket with the resulting bearer access token.

The current security configuration requires authentication for production pages and APIs but does not yet restrict
individual endpoints by these authorities. Fine-grained endpoint authorization using the documented permissions is a
separate follow-up.

### Verify the configuration

1. Start JTicket with the `production` profile.
2. Open the back-office root URL. JTicket should redirect to Auth0 Universal Login.
3. Sign in with an Auth0 user. Auth0 should return to `/login/oauth2/code/jticket`.
4. Confirm that the back-office pages and browser calls to `/api/**` work with the same session.
5. Request an access token whose audience is the JTicket API identifier.
6. Call an API with `Authorization: Bearer ACCESS_TOKEN`.
7. Confirm that a request without a session or bearer token receives `401` for `/api/**`.

The development profile intentionally permits unauthenticated access and does not exercise the Auth0 configuration.

## Scaling
There is no complex clustering configuration of scaling out the system as we are leveraging the in-memory database as 
the only sharing points among working nodes. So as long as we can make Ignite cluster running good, JTicket cluster will 
work great accordingly. 

## What's next
Add and verify fine-grained endpoint authorization using the documented OAuth2 scopes.
