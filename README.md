# KafkaSync

A project to convert a async flow on Kafka to a sync http request.

Technologies

- Kafka 
- Kafka Streams
- Akka
- Zookeeper
- JSON Schema

## Commands

Run K6:

```sh
k6 run ./k6.js
```

Create topics:

```sh
./create_topics.sh
```

Delete topics:

```sh
./delete_topics.sh
```

## K6 output

```sh
running (0m30.0s), 00/10 VUs, 96070 complete and 0 interrupted iterations
default ✓ [======================================] 10 VUs  30s

data_received..................: 22 MB 717 kB/s
data_sent......................: 16 MB 544 kB/s
http_req_blocked...............: avg=2.53µs  min=1.01µs   med=2.07µs  max=1.75ms   p(90)=2.99µs  p(95)=3.34µs
http_req_connecting............: avg=22ns    min=0s       med=0s      max=299.93µs p(90)=0s      p(95)=0s    
http_req_duration..............: avg=3.02ms  min=405.77µs med=2.19ms  max=102.26ms p(90)=4.72ms  p(95)=6.56ms
{ expected_response:true }...: avg=3.02ms  min=405.77µs med=2.19ms  max=102.26ms p(90)=4.72ms  p(95)=6.56ms
http_req_failed................: 0.00% ✓ 0           ✗ 96070
http_req_receiving.............: avg=36.93µs min=12.66µs  med=26.03µs max=4.54ms   p(90)=48.43µs p(95)=59.3µs
http_req_sending...............: avg=14.49µs min=6µs      med=11.37µs max=9.85ms   p(90)=16.19µs p(95)=18.4µs
http_req_tls_handshaking.......: avg=0s      min=0s       med=0s      max=0s       p(90)=0s      p(95)=0s    
http_req_waiting...............: avg=2.96ms  min=350.84µs med=2.14ms  max=101.65ms p(90)=4.65ms  p(95)=6.49ms
http_reqs......................: 96070 3201.497671/s
iteration_duration.............: avg=3.11ms  min=492.56µs med=2.27ms  max=103.43ms p(90)=4.85ms  p(95)=6.69ms
iterations.....................: 96070 3201.497671/s
vus............................: 10    min=10        max=10 
vus_max........................: 10    min=10        max=10 
```
