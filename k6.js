import http from 'k6/http';
import { sleep } from 'k6';

var id = 1

export const options = {
  vus: 1,
  duration: '5s',
};

export default function () {
  const url = 'http://localhost:4000/validations';

    const payload = JSON.stringify({
       "id": __ITER.toString(),
       "amount": Math.floor(Math.random() * 1000)
   });

    const params = {
      headers: {
        'Content-Type': 'application/json',
      },
    };

    http.post(url, payload, params);

    id++
};
