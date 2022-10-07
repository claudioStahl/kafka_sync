A project to convert a async flow on Kafka to a sync http request.

This project use the Kafka Streams and Akka.

Run K6:

```sh
k6 run ./k6.js
```

K6 output

```sh
iteration_duration.............: avg=5.81ms  min=3.73ms  med=5.55ms  max=22.34ms  p(90)=7.37ms  p(95)=7.86ms
iterations.....................: 857    171.307399/s
```
