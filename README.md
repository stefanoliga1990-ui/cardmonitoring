# Card Monitoring

Applicazione Spring Boot per calcolare e monitorare nel tempo il prezzo medio delle offerte attive CardTrader.

## Avvio locale

Requisiti: Java 17. Il Maven Wrapper usa il `settings.xml` locale al progetto, senza modificare le impostazioni Maven globali.

1. Copiare `secrets/application-secrets.example.properties` in `secrets/application-secrets.properties`.
2. Inserire il token CardTrader nella proprietà `cardtrader.token`.
3. Avviare l'applicazione:

```powershell
.\mvnw.cmd -s .\settings.xml -gs .\settings.xml spring-boot:run
```

L'applicazione è disponibile su `http://localhost:8080`.

## Pubblicazione su Railway

Railway può rilevare e costruire automaticamente il progetto Maven tramite Railpack. Non sono necessari Dockerfile, Procfile o comandi di build personalizzati.

Variabili richieste:

```text
CARDTRADER_TOKEN=<token CardTrader>
CARDMONITORING_DATA_PATH=/data
SPRING_PROFILES_ACTIVE=prod
CARDTRADER_EXPECTED_CURRENCY=EUR
MONITORING_SCHEDULER_CRON=0 0 3 * * MON
MONITORING_SCHEDULER_TIME_ZONE=Europe/Rome
```

Configurazione del servizio:

- montare un volume persistente in `/data`;
- mantenere una sola istanza;
- disabilitare Serverless;
- usare `/actuator/health` come healthcheck;
- generare un dominio pubblico HTTPS.

Railway fornisce automaticamente la variabile `PORT`, già supportata dall'applicazione.

## Dati locali e segreti

Le directory `secrets/` e `data/`, i file di build e il `settings.xml` locale sono esclusi da Git. È versionato soltanto il file di esempio dei segreti, privo di token.
