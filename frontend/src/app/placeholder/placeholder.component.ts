import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-placeholder',
  templateUrl: './placeholder.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PlaceholderComponent {
  readonly title = inject(ActivatedRoute).snapshot.data['title'] as string;
}
