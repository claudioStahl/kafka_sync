import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
  vus: 10,
  duration: '30s',
};

export default function () {
  const url = 'http://localhost:4000/validations';

  const payload = JSON.stringify({
       "id": "k6-" + Date.now() + "-" + Math.floor(Math.random() * 100000) + "-" + __ITER.toString(),
       "amount": Math.floor(Math.random() * 1000)
   });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(url, payload, params);

  check(res, {
    'is status 200': (r) => r.status === 200,
  });

  if (res.status !== 200) {
    console.error(res)
  }
};
