```bash
sbt test
sbt run
```

Tested w/ java 17

```
curl -X POST -d '{"marketId":123456,"selectionId":987654,"odds":2.2,"stake":253.67,"currency":"USD","date":"2021-05-18T21:32:42.324Z"}' http://localhost:8080/api/v1/conversion/trade
```


