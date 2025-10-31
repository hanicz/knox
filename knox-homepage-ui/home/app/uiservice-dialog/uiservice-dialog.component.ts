/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatGridListModule } from '@angular/material/grid-list';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-uiservice-dialog',
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatGridListModule, CommonModule],
  templateUrl: './uiservice-dialog.component.html',
  styleUrl: './uiservice-dialog.component.css'
})
export class UiserviceDialogComponent {
  enableServiceText = false;
  filteredServiceUrls: string[] = [];

  constructor(@Inject(MAT_DIALOG_DATA) public data: any) {
    this.filteredServiceUrls = [...(data?.serviceUrls || [])];
  }

  filterServiceUrls(searchTerm: string): void {
    const term = searchTerm.toLowerCase();
    this.filteredServiceUrls = (this.data.serviceUrls || []).filter(url =>
      url.toLowerCase().includes(term)
    );
  }

  getServiceUrlHostAndPort(url: string): string {
    try {
      const parsedUrl = new URL(url);
      return `(${parsedUrl.hostname}:${parsedUrl.port || (parsedUrl.protocol === 'https:' ? '443' : '80')})`;
    } catch (e) {
      return '';
    }
  }
}
