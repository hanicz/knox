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

import {Component, OnInit, ViewChild} from '@angular/core';
import {ResourceTypesService} from "../resourcetypes/resourcetypes.service";
import {ResourceService} from "../resource/resource.service";
import {BsModalComponent} from "ng2-bs3-modal";
import {ProviderConfig} from "../resource-detail/provider-config";
import {AuthenticationWizard} from "./authentication-wizard";
import {CategoryWizard} from "./category-wizard";
import {AuthorizationWizard} from "./authorization-wizard";
import {IdentityAssertionWizard} from "./identity-assertion-wizard";
import {HaWizard} from "./ha-wizard";
import {Resource} from "../resource/resource";
import {DisplayBindingProviderConfig} from "./display-binding-provider-config";

@Component({
  selector: 'app-provider-config-wizard',
  templateUrl: './provider-config-wizard.component.html',
  styleUrls: ['./provider-config-wizard.component.css']
})
export class ProviderConfigWizardComponent implements OnInit {

  private static CATEGORY_STEP = 1;
  private static TYPE_STEP     = 2;
  private static PARAMS_STEP   = 3;

  // Provider Categories
  private static CATEGORY_AUTHENTICATION: string  = 'Authentication';
  private static CATEGORY_AUTHORIZATION: string   = 'Authorization';
  private static CATEGORY_ID_ASSERTION: string    = 'Identity Assertion';
  private static CATEGORY_HA: string              = 'HA';
  private static providerCategories: string[] = [ ProviderConfigWizardComponent.CATEGORY_AUTHENTICATION,
                                                  ProviderConfigWizardComponent.CATEGORY_AUTHORIZATION,
                                                  ProviderConfigWizardComponent.CATEGORY_ID_ASSERTION,
                                                  ProviderConfigWizardComponent.CATEGORY_HA
                                                ];

  private static CATEGORY_TYPES: Map<string, CategoryWizard> =
            new Map([
              [ProviderConfigWizardComponent.CATEGORY_AUTHENTICATION, new AuthenticationWizard() as CategoryWizard],
              [ProviderConfigWizardComponent.CATEGORY_AUTHORIZATION,  new AuthorizationWizard() as CategoryWizard],
              [ProviderConfigWizardComponent.CATEGORY_ID_ASSERTION,   new IdentityAssertionWizard() as CategoryWizard],
              [ProviderConfigWizardComponent.CATEGORY_HA,             new HaWizard() as CategoryWizard]
            ]);

  @ViewChild('newProviderConfigModal')
  childModal: BsModalComponent;

  private step: number = 0;

  name: String = '';

  providers: Array<ProviderConfig> = [];

  selectedCategory: string;

  constructor(private resourceTypesService: ResourceTypesService, private resourceService: ResourceService) { }

  ngOnInit() {
    this.selectedCategory = ProviderConfigWizardComponent.CATEGORY_AUTHENTICATION; // Default to authentication
  }

  open(size?: string) {
    this.reset();
    this.childModal.open(size ? size : 'lg');
  }

  reset() {
    this.step = 0;
    this.name = '';
    this.providers = [];
    this.selectedCategory = ProviderConfigWizardComponent.CATEGORY_AUTHENTICATION;
  }

  onFinishAdd() {
    console.debug('Selected provider category: ' + this.selectedCategory);

    let catWizard = this.getCategoryWizard(this.selectedCategory);
    let type = catWizard ? catWizard.getSelectedType() : 'undefined';
    console.debug('Selected provider type: ' + type);

    if (catWizard) {
      let pc: ProviderConfig = catWizard.getProviderConfig();
      if (pc) {
        this.providers.push(pc);
        console.debug('\tProvider Name: ' + pc.name);
        console.debug('\tProvider Role: ' + pc.role);
        console.debug('\tProvider Enabled: ' + pc.enabled);
        if (pc.params) {
          for (let name of Object.getOwnPropertyNames(pc.params)) {
            console.debug('\t\tParam: ' + name + ' = ' + pc.params[name]);
          }
        } else {
          console.debug('\tNo Params');
        }
      }
    }

    this.step = 0; // Return to the beginning
  }

  onClose() {
    console.debug('Provider Configuration: ' + this.name);

    for (let pc of this.providers) {
      console.debug('\tProvider: ' + pc.name + ' (' + pc.role + ')');
    }

    // Identify the new resource
    let newResource = new Resource();
    newResource.name = this.name + '.json';

    // Persist the new provider configuration
    this.resourceService.createResource('Provider Configurations',
                                        newResource,
                                        this.resourceService.serializeProviderConfiguration(this.providers, 'json'))
                        .then(() => {
                          // Reload the resource list presentation
                          this.resourceTypesService.selectResourceType('Provider Configurations');

                          // Set the new descriptor as the selected resource
                          this.resourceService.getProviderConfigResources().then(resources => {
                            for (let res of resources) {
                              if (res.name === newResource.name) {
                                this.resourceService.selectedResource(res);
                                break;
                              }
                            }
                          });
                        });
  }

  getStep(): number {
    return this.step;
  }

  onNextStep() {
    ++this.step;
  }

  onPreviousStep() {
    --this.step;
  }

  hasMoreSteps(): boolean {
    let result = false;
    let catWizard = this.getCategoryWizard(this.selectedCategory);
    if (catWizard) {
      result = (this.step < (catWizard.getSteps() - 1));
    }
    return result;
  }

  isRootStep(): boolean {
    return (this.step === 0);
  }

  isProviderCategoryStep(): boolean {
    return (this.step === ProviderConfigWizardComponent.CATEGORY_STEP);
  }

  isProviderTypeStep(): boolean {
    return (this.step === ProviderConfigWizardComponent.TYPE_STEP);
  }

  isProviderParamsStep(): boolean {
    return (this.step === ProviderConfigWizardComponent.PARAMS_STEP);
  }

  getProviderCategories() : string[] {
    return ProviderConfigWizardComponent.providerCategories;
  }

  getCategoryWizard(category?: string): CategoryWizard {
    return ProviderConfigWizardComponent.CATEGORY_TYPES.get(category ? category : this.selectedCategory);
  }

  getProviderTypes(category?: string) : string[] {
    let catWizard = this.getCategoryWizard(category);
    if (catWizard) {
      return catWizard.getTypes();
    } else {
      console.debug('Unresolved category wizard for ' + (category ? category : this.selectedCategory));
    }
    return [];
  }

  getProviderParams(): string[] {
    let catWizard = this.getCategoryWizard();
    if (catWizard) {
      let pc = catWizard.getProviderConfig();
      if (pc) {
        if (pc instanceof DisplayBindingProviderConfig) {
          let dispPC = pc as DisplayBindingProviderConfig;
          return dispPC.getDisplayPropertyNames();
        } else {
          console.debug('Got Vanilla ProviderConfig');
          return [];
        }
      } else {
        console.log('No provider config from category wizard ' + typeof(catWizard));
      }
    } else {
      console.debug('Unresolved category wizard for ' + this.selectedCategory);
    }
    return [];
  }

  setProviderParamBinding(name: string, value: string) {
    let catWizard = this.getCategoryWizard();
    if (catWizard) {
      let pc = catWizard.getProviderConfig();
      if (pc) {
        if (pc instanceof DisplayBindingProviderConfig) {
          let dispPC = pc as DisplayBindingProviderConfig;
          let property = dispPC.getDisplayNamePropertyBinding(name);
          pc.setParam(property, value);
          console.debug('Set ProviderConfig param value: ' + property + '=' + value);
        }
      }
    }
  }

  getProviderParamBinding(name: string): string {
    let catWizard = this.getCategoryWizard();
    if (catWizard) {
      let pc = catWizard.getProviderConfig();
      if (pc) {
        if (pc instanceof DisplayBindingProviderConfig) {
          let dispPC = pc as DisplayBindingProviderConfig;
          let value = pc.getParam(dispPC.getDisplayNamePropertyBinding(name));
          return (value ? value : '');
        }
      }
    }
    return '';
  }

}