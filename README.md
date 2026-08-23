# Secure Software Development Kurs

## Potreban softver

Da biste pratili vežbe, potrebno je da instalirate sledeći softver

* JAVA 8 SDK (https://adoptopenjdk.net/?variant=openjdk8&jvmVariant=hotspot)
* IntelliJ Ultimate (https://www.jetbrains.com/idea/download/#section=windows) i studentska licenca
* GIT (https://git-scm.com/downloads)

Za čas "Cross site request forgery (CSRF)"
* Node.js LTS (https://nodejs.org/en/download/)

Za čas "Alati za statičku i dinamičku analizu"
* OWASP ZAP (https://www.zaproxy.org/download/)
* SonarQube (https://binaries.sonarsource.com/Distribution/sonarqube/sonarqube-8.5.1.38104.zip)

Za čas "Autentifikacija"
* Neki od TOTP autentifikatora za mobilne uređaje (Google/Microsoft Authenticator, FreeOTP, OTP Auth...)

## MongoDB katalog servisa

Aplikacija koristi Spring Boot embedded MongoDB preko Flapdoodle biblioteke. Zaseban MongoDB server
ili Docker kontejner nisu potrebni za podrazumevano lokalno pokretanje. Pri prvom pokretanju Flapdoodle
može preuzeti MongoDB binary za trenutnu platformu.

Mongo se pokreće na nasumičnom slobodnom portu, a početni podaci se idempotentno učitavaju iz:

* `src/main/resources/mongo/parts.json`;
* `src/main/resources/mongo/service-types.json`;
* `src/main/resources/mongo/service-details.json`.

`data.sql` sadrži SQL service zapise sa stabilnim ID-jevima koje referencira `service-details.json`.
Seed se može isključiti za eksternu ili trajnu MongoDB bazu:

```properties
app.mongodb.seed-enabled=false
spring.data.mongodb.uri=mongodb://localhost:27017/secure-software-development
```

Namerno ranjiva demonstraciona pretraga kataloga delova nalazi se na
`POST /api/catalog/parts/search`. Normalan filter izgleda ovako:

```json
{"filters":{"name":"Front brake pad set"}}
```

Operatorski filter kojim se u izolovanom nastavnom okruženju demonstrira proširen rezultat:

```json
{"filters":{"name":{"$ne":null}}}
```

Automatski testovi verifikuju proširen rezultat bez pokušaja da iscrpe memoriju ili obore JVM.
