# Aufgabe Schmidt

## Aufgabenstellung

### Erstelle ein Full Stack System Frontend / Backend mit folgender Aufgabestellung:

* Das Backend ruft zyklisch (alle paar Sekunden) die Blogbeiträge von der Seite thekey.academy ab (über die Wordpress API - https://developer.wordpress.org/rest-api/reference/posts/)
* Das Backend verarbeitet die Blogbeiträge zu einer einfachen Word Count Map ({"und": 5, "der": 3, ...})
* Das Backend sendet nach der Verarbeitung die Map per WebSocket an das Frontend
* Das Frontend zeigt die Word Count Map der neuen Beiträge an und aktualisiert sich selbstständig neu bei neuen Daten.

### Bonuspunkte:
* Eventgetriebene Verarbeitung
* Aktualisierung im Frontend nur bei tatsächlich neuen Blogbeiträgen - nicht immer komplett neu
* Microservice-Architektur

### Programmiersprachen:
* Backend in einer gängigen, modernen Programmiersprache (zB Scala, Java, C#)- Frameworks dürfen gerne genutzt werden
* Frontend kann extrem basic sein, es dient nur dazu die Kommunikation mit dem Backend abzubilden.
* Datenspeicherung gerne in-memory

Was wollen wir sehen:
- hohe Codequalität
- Testabdeckung
- Production-ready code - so wie du auch eine Aufgabe hier in der Firma lösen würdest
- Abgabe bitte als github mit Anweisungen wie wir es testen können innerhalb von 1 Woche oder bis zum Wunschtermin.

## Lösung

Wie ihr sehen werdet, habe ich diverse libs benutzt. Das backend läuft als µ-Service, der jedoch keinen externen accesspoint hat. 

Das Frontend ist als Akka HTTP Frontend gelöst. Ein Playframework finde ich hier viel zu groß und letztlich auch nicht ziehlführend.


## Starten und nutzen

### Presets

Bitte vorher als docker eine PostgreeSQL starte. Leider nicht in-memory machbar. Bitte den Pfad zum primaryInit.sql anpassen!

```shell
docker compose up -d
```

```shell
docker exec -i postgres-db psql -U shopping-cart -t < primaryInit.sql
```

### Run 

#### Backend starten

```shell
sbt
project backend
run
```