# WebSockets

## Endpoint

```
/ws/sessions          (STOMP over WebSocket, SockJS not required)
```

Configured by `WebSocketConfig`: a simple in-memory STOMP broker (`/topic` prefix) with
application-destination prefix `/app` (currently unused — clients only subscribe, they never
publish). See the "Scalability note" in `docs/ARCHITECTURE.md` for how this would be swapped for
an external broker (e.g. RabbitMQ STOMP) without changing any application code, since publishing
goes through the `SessionEventPublisher` abstraction.

## Authentication strategy

There is no separate WebSocket login step. The same JWT used for REST calls is presented on the
STOMP `CONNECT` frame, either as:

- A native STOMP header `Authorization: Bearer <token>`, or
- A native STOMP header `access_token: <token>`

`WebSocketAuthChannelInterceptor` validates the JWT on `CONNECT` (same `JwtService` as the REST
JWT filter) and attaches a `UserPrincipal` to the STOMP session. A missing/invalid/expired token
causes the `CONNECT` to be rejected.

## Subscription authorization

The interceptor also authorizes every `SUBSCRIBE` frame:

| Destination | Who may subscribe |
|--------------|--------------------|
| `/topic/session/{workoutId}` | Only the workout's owner |
| `/topic/user/{userId}/sessions` | Only that same `userId` |

Any other destination pattern, or a subscription to another user's workout/user topic, is
rejected. This is what prevents users from casually listening in on someone else's private
workout session.

## Event envelope

```json
{
  "eventId": "3fbd...-uuid",
  "eventType": "SET_LOGGED",
  "occurredAt": "2026-07-17T20:30:00Z",
  "workoutId": "a1b2...-uuid",
  "data": { "setId": "..." }
}
```

`eventType` is one of: `SESSION_STARTED`, `SET_LOGGED`, `SET_UPDATED`, `SET_DELETED`,
`SESSION_COMPLETED`. The envelope shape is intentionally stable/versionable: new event types or
new `data` keys can be added without breaking existing subscribers, since consumers should treat
unknown fields as ignorable.

| Event | Sent to | `data` |
|-------|---------|--------|
| `SESSION_STARTED` | `/topic/session/{workoutId}` and `/topic/user/{userId}/sessions` | `{ "userId": "..." }` |
| `SESSION_COMPLETED` | same as above | `{ "userId": "..." }` |
| `SET_LOGGED` / `SET_UPDATED` / `SET_DELETED` | `/topic/session/{workoutId}` only | `{ "setId": "..." }` |

## Commit-then-publish

Every event is published from an `afterCommit` transaction-synchronization callback
(`TransactionUtils.runAfterCommit`), never from inside the `@Transactional` method body itself.
If the surrounding transaction rolls back, nothing is published. See docs/CONCURRENCY.md.

## Manual testing guide

1. Get a JWT: `POST /api/auth/register` or `/api/auth/login`.
2. Using a STOMP-capable client (e.g. a browser console with `@stomp/stompjs`, or a desktop STOMP
   client), connect to `ws://localhost:8080/ws/sessions` and send `CONNECT` with header
   `Authorization: Bearer <token>`.
3. `SUBSCRIBE` to `/topic/user/<your-user-id>/sessions` and/or `/topic/session/<workout-id>`.
4. In another terminal, drive the REST API (start a workout, log a set, complete it) and watch
   the matching events arrive on the subscribed topics.

Example browser console snippet (with `@stomp/stompjs` loaded):

```js
const client = new StompJs.Client({
  brokerURL: "ws://localhost:8080/ws/sessions",
  connectHeaders: { Authorization: "Bearer " + token },
  onConnect: () => {
    client.subscribe(`/topic/user/${userId}/sessions`, (msg) => console.log(JSON.parse(msg.body)));
  },
});
client.activate();
```

## Known limitation

The simple broker keeps subscriptions in-process. It does not fan out across multiple application
instances — running more than one replica means a client is only guaranteed to see events
published by the instance it is connected to. Documented as a known limitation rather than solved
here; see `docs/FUTURE_ROADMAP.md`.
