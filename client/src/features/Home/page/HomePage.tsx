import { PageLayout } from '@/shared/components/PageLayout';

import { Description } from '../components/Description';
import { Info } from '../components/Info';

export const HomePage = () => {
  return (
    <PageLayout>
      <Info />
      <Description />
    </PageLayout>
  );
};
