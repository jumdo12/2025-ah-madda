import { useMutation, useQueryClient } from '@tanstack/react-query';

import { fetcher } from '../fetcher';
import { eventQueryKeys } from '../queries/event';
import { Answer } from '../types/event';

export const postEventParticipation = (
  eventId: number,
  applicationFormVersionId: number,
  answers: Answer[]
) => {
  return fetcher.post<void>(`events/${eventId}/participation`, {
    applicationFormVersionId,
    answers,
  });
};

export const useParticipateEvent = (eventId: number, applicationFormVersionId: number) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (answers: Answer[]) =>
      postEventParticipation(eventId, applicationFormVersionId, answers),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: [...eventQueryKeys.guestStatus(), eventId],
      });
    },
  });
};
