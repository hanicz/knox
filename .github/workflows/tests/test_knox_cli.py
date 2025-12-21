# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import unittest
from util.knox import Knox

########################################################
# These tests are validating the KnoxCLI commands
########################################################

class TestKnoxCLI(unittest.TestCase):
    def setUp(self):
        self.knox = Knox("compose-knox-1")
        self.knox_cli_path = "/knox-runtime/bin/knoxcli.sh"

    def test_knox_cli_create_alias(self):
        """
            Validate creation of alias

            Step:
            - Execute:
            knoxcli.sh create-alias test --cluster cluster1 --value test_value --generate

            Result:
            should export the identity cert file
        :return:
        """
        print(f"\nTesting create-alias command")
        cmd = "{} create-alias test_key --cluster cluster1 --value test_value --generate".format(self.knox_cli_path)
        cmd_output = list(self.knox.run_knox_cmd(cmd).output)
        self.assertTrue(self.check_cli_output(iter(cmd_output), 'test_key has been successfully created'))
        print(f"Verified create-alias command output contains new alias")

    def check_cli_output(self, cmd_output, match_string=None):
        """
        Match the passed string against the output of knoxcli.sh execution
        :param cmd_output: knoxcli.sh cmd output
        :param match_string: String to match in knoxcli output
        :return: True if match is found else False
        """
        expected_matches = False
        while True:
            try:
                item = next(cmd_output)
                print(f"{item}")
                if match_string in str(item):
                    expected_matches = True
                    break
            except StopIteration:
                break
        return expected_matches
