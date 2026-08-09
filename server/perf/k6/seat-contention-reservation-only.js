import exec from 'k6/execution';
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '1';
const TOKEN_FILE = __ENV.TOKEN_FILE || '/tmp/ahmadda-perf-access-tokens.csv';
const RESERVATION_REQUEST_COUNT = Number(__ENV.RESERVATION_REQUEST_COUNT || '300');
const RESERVATION_RATE_PER_SECOND = Number(
        __ENV.RESERVATION_RATE_PER_SECOND || RESERVATION_REQUEST_COUNT
);

const reservationSuccess = new Counter('reservation_success');
const reservationSoldOut = new Counter('reservation_sold_out');
const reservationUnexpected = new Counter('reservation_unexpected');
const reservationDuration = new Trend('reservation_duration', true);
const reservationSuccessDuration = new Trend('reservation_success_duration', true);
const reservationSoldOutDuration = new Trend('reservation_sold_out_duration', true);

http.setResponseCallback(http.expectedStatuses(202, 422));

const participants = new SharedArray('participants', () => {
    const lines = open(TOKEN_FILE)
            .trim()
            .split('\n');

    return lines.slice(1)
            .filter((line) => line.trim().length > 0)
            .map((line) => {
                const [memberId, email, accessToken] = line.trim().split(',');

                return {
                    memberId,
                    email,
                    accessToken,
                };
            });
});

if (participants.length < RESERVATION_REQUEST_COUNT) {
    throw new Error(
            `At least ${RESERVATION_REQUEST_COUNT} access tokens are required, but ${participants.length} were loaded`
    );
}

export const options = {
    discardResponseBodies: true,
    scenarios: {
        reservation_only: {
            executor: 'constant-arrival-rate',
            exec: 'participateEvent',
            rate: RESERVATION_RATE_PER_SECOND,
            timeUnit: '1s',
            duration: '1s',
            preAllocatedVUs: RESERVATION_REQUEST_COUNT,
            maxVUs: RESERVATION_REQUEST_COUNT,
            gracefulStop: '30s',
            tags: {
                workload: 'reservation-only',
            },
        },
    },
    thresholds: {
        reservation_unexpected: ['count==0'],
    },
};

export function participateEvent() {
    const participantIndex = exec.scenario.iterationInTest;
    const participant = participants[participantIndex];

    if (participantIndex >= RESERVATION_REQUEST_COUNT) {
        return;
    }

    if (!participant) {
        reservationUnexpected.add(1);
        return;
    }

    const response = http.post(
            `${BASE_URL}/api/events/${EVENT_ID}/participation`,
            JSON.stringify({ answers: [] }),
            {
                headers: {
                    Authorization: `Bearer ${participant.accessToken}`,
                    'Content-Type': 'application/json',
                },
                tags: {
                    name: 'POST /api/events/:eventId/participation',
                },
                responseType: 'text',
            }
    );

    reservationDuration.add(response.timings.duration);

    if (response.status === 202) {
        reservationSuccess.add(1);
        reservationSuccessDuration.add(response.timings.duration);
    } else if (
        response.status === 422
        && response.body.includes('수용 인원이 가득차')
    ) {
        reservationSoldOut.add(1);
        reservationSoldOutDuration.add(response.timings.duration);
    } else {
        reservationUnexpected.add(1);
    }

    check(response, {
        'reservation response is success or sold out': (result) =>
            result.status === 202
            || (
                result.status === 422
                && result.body.includes('수용 인원이 가득차')
            ),
    });
}
