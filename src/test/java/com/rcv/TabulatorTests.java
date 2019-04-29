/*
 * Ranked Choice Voting Universal Tabulator
 * Copyright (c) 2018 Jonathan Moldover, Louis Eisenberg, and Hylton Edingfield
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this
 * program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * class TabulatorTests
 * purpose: these regression tests run various tabulations and compare the generated results to
 * expected results.  Passing these tests ensures that changes to tabulation code have not
 * altered the results of the tabulation.
 *
 */

package com.rcv;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TabulatorTests {

  // folder where we store test inputs
  private static final String TEST_ASSET_FOLDER = "src/test/resources/test_data";
  // limit log output to avoid spam
  private static final Integer MAX_LOG_ERRORS = 10;

  // function: fileCompare
  // purpose: compare file contents line by line to identify any differences and give an
  // indication of where they lie
  // param: path1 path to first file to be compared
  // param: path2 path to second file to be compared
  // returns: true if the file contents are equal otherwise false
  // file access: read-only for path1 and path2
  private static boolean fileCompare(String path1, String path2) {
    // result will be true if file contents are equal - assume equal until learning otherwise
    boolean result = true;
    try {
      // reader1 and reader2 are used to read lines from the files for comparison
      BufferedReader reader1 = new BufferedReader(new FileReader(path1));
      BufferedReader reader2 = new BufferedReader(new FileReader(path2));
      // track current line to tell user where problems occur
      int currentLine = 1;
      // track output to avoid spam
      int errorCount = 0;
      // max outputs

      // loop until EOF is encountered
      while (true) {
        // line1 and line2 store current line read from readers or null if EOF
        String line1 = reader1.readLine();
        String line2 = reader2.readLine();
        // see if the files are done
        if (line1 == null && line2 == null) {
          // both files ended
          break;
        } else if (line1 == null || line2 == null) {
          // one file ended but the other did not
          Logger.log(Level.SEVERE, "Files are unequal lengths!");
          result = false;
          break;
        }
        // both files have content so compare it
        if (!(line1.contains("GeneratedDate") && line2.contains("GeneratedDate"))
            && !line1.equals(line2)) {
          // update flags and report inequality
          errorCount++;
          result = false;
          Logger.log(
              Level.SEVERE, "Files are not equal (line %d):\n%s\n%s", currentLine, line1, line2);
          // see if we should keep processing
          if (errorCount >= MAX_LOG_ERRORS) {
            break;
          }
        }
        currentLine++;
      }
    } catch (FileNotFoundException e) {
      Logger.log(Level.SEVERE, "File not found!\n%s", e.toString());
      result = false;
    } catch (IOException e) {
      Logger.log(Level.SEVERE, "Error reading file!\n%s", e.toString());
      result = false;
    }
    return result;
  }

  // function: getTestFilePath
  // purpose: given stem and suffix returns path to file in test asset folder
  // returns: path to file in test folder
  private static String getTestFilePath(String stem, String suffix) {
    return Paths.get(System.getProperty("user.dir"), TEST_ASSET_FOLDER, stem, stem + suffix)
        .toAbsolutePath()
        .toString();
  }

  // function: runTabulationTest
  // purpose: helper function to support running various tabulation tests
  // param: stem base name of folder containing config file cvr files and expected result files
  private static void runTabulationTest(String stem) {
    String configPath = getTestFilePath(stem, "_config.json");
    // create a session object and run the tabulation
    TabulatorSession session = new TabulatorSession(configPath);
    session.tabulate();

    String timestampString = session.getTimestampString();
    ContestConfig config = ContestConfig.loadContestConfig(configPath);
    assertNotNull(config);

    if (config.isSequentialMultiSeatEnabled()) {
      for (int i = 1; i <= config.getNumberOfWinners(); i++) {
        compareJsons(config, stem, timestampString, i);
      }
    } else {
      compareJsons(config, stem, timestampString, null);
    }

    // test passed so cleanup test output folder
    File outputFolder = new File(session.outputPath);
    if (outputFolder.listFiles() != null) {
      //noinspection ConstantConditions
      for (File file : outputFolder.listFiles()) {
        if (!file.isDirectory()) {
          //noinspection ResultOfMethodCallIgnored
          file.delete();
        }
      }
    }
  }

  private static void compareJsons(
      ContestConfig config, String stem, String timestampString, Integer sequentialNumber) {
    compareJson(config, stem, "summary", timestampString, sequentialNumber);
    if (config.isGenerateCdfJsonEnabled()) {
      compareJson(config, stem, "cvr_cdf", timestampString, sequentialNumber);
    }
  }

  private static void compareJson(
      ContestConfig config,
      String stem,
      String jsonType,
      String timestampString,
      Integer sequentialNumber) {
    String actualOutputPath =
        ResultsWriter.getOutputFilePath(
                config.getOutputDirectory(), jsonType, timestampString, sequentialNumber)
            + ".json";
    String expectedPath =
        getTestFilePath(
            stem,
            ResultsWriter.sequentialSuffixForOutputPath(sequentialNumber)
                + "_expected_"
                + jsonType
                + ".json");
    assertTrue(fileCompare(expectedPath, actualOutputPath));
  }

  // function: setup
  // purpose: runs once at the beginning of testing to setup logging
  @BeforeAll
  static void setup() {
    try {
      Logger.setup();
    } catch (IOException exception) {
      // this is non-fatal
      System.err.print(String.format("Failed to start system logging!\n%s", exception.toString()));
    }
  }

  // function: invalidParamsTest
  // purpose: test invalid params in config file
  @Test
  @DisplayName("test invalid params in config file")
  void invalidParamsTest() {
    String configPath = getTestFilePath("invalid_params_test", "_config.json");
    ContestConfig config = ContestConfig.loadContestConfig(configPath);
    assertNotNull(config);
    assertFalse(config.validate());
  }

  // function: invalidSourcesTest
  // purpose: test invalid source files
  @Test
  @DisplayName("test invalid source files")
  void invalidSourcesTest() {
    String configPath = getTestFilePath("invalid_sources_test", "_config.json");
    ContestConfig config = ContestConfig.loadContestConfig(configPath);
    assertNull(config);
  }

  // function: testPortlandMayor
  // purpose: test tabulation of Portland contest
  @Test
  @DisplayName("2015 Portland Mayor")
  void testPortlandMayor() {
    runTabulationTest("2015_portland_mayor");
  }

  // function: testPortlandMayor
  // purpose: test tabulation of Portland contest using candidate codes
  @Test
  @DisplayName("2015 Portland Mayor Candidate Codes")
  void testPortlandMayorCodes() {
    runTabulationTest("2015_portland_mayor_codes");
  }

  // function: test2013MinneapolisMayorScale
  // purpose: test large scale (1,000,000+) cvr contest
  @Test
  @DisplayName("2013 Minneapolis Mayor Scale")
  void test2013MinneapolisMayorScale() {
    runTabulationTest("2013_minneapolis_mayor_scale");
  }

  // function: testContinueUntilTwoCandidatesRemain
  // purpose: test rule to continue tabulation until only two candidates remain potentially after
  // all winner(s) have been elected
  @Test
  @DisplayName("Continue Until Two Candidates Remain")
  void testContinueUntilTwoCandidatesRemain() {
    runTabulationTest("continue_tabulation_test");
  }

  // function: test2017MinneapolisMayor
  // purpose: test 2017 Minneapolis Mayor contest
  @Test
  @DisplayName("2017 Minneapolis Mayor")
  void test2017MinneapolisMayor() {
    runTabulationTest("2017_minneapolis_mayor");
  }

  // function: test2013MinneapolisMayor
  // purpose: test 2013 Minneapolis Mayor contest
  @Test
  @DisplayName("2013 Minneapolis Mayor")
  void test2013MinneapolisMayor() {
    runTabulationTest("2013_minneapolis_mayor");
  }

  // function: test2013MinneapolisPark
  // purpose: test 2013 Minneapolis Park contest
  @Test
  @DisplayName("2013 Minneapolis Park")
  void test2013MinneapolisPark() {
    runTabulationTest("2013_minneapolis_park");
  }

  // function: test2018MaineGovPrimaryDem
  // purpose: test 2018 Maine Governor Democratic Primary contest
  @Test
  @DisplayName("2018 Maine Governor Democratic Primary")
  void test2018MaineGovPrimaryDem() {
    runTabulationTest("2018_maine_governor_primary");
  }

  // function: testMinneapolisMultiSeatThreshold
  // purpose: test testMinneapolisMultiSeatThreshold
  @Test
  @DisplayName("testMinneapolisMultiSeatThreshold")
  void testMinneapolisMultiSeatThreshold() {
    runTabulationTest("minneapolis_multi_seat_threshold");
  }

  // function: testDuplicate
  // purpose: test for overvotes
  @Test
  @DisplayName("test for overvotes")
  void testDuplicate() {
    runTabulationTest("duplicate_test");
  }

  // function: testExcludedCandidate
  // purpose: test excluding candidates in config file
  @Test
  @DisplayName("test excluding candidates in config file")
  void testExcludedCandidate() {
    runTabulationTest("excluded_test");
  }

  // function: testMinimumThreshold
  // purpose: test minimum vote threshold setting
  @Test
  @DisplayName("test minimum vote threshold setting")
  void testMinimumThreshold() {
    runTabulationTest("minimum_threshold_test");
  }

  // function: testSkipToNext
  // purpose: test skipping to next candidate after overvote
  @Test
  @DisplayName("test skipping to next candidate after overvote")
  void testSkipToNext() {
    runTabulationTest("skip_to_next_test");
  }

  // function: testHareQuota
  // purpose: tests Hare quota
  @Test
  @DisplayName("test Hare quota")
  void testHareQuota() {
    runTabulationTest("2013_minneapolis_park_hare");
  }

  // function: testSequentialMultiSeat
  // purpose: tests sequentialMultiSeat option
  @Test
  @DisplayName("test sequential multi-seat logic")
  void testSequentialMultiSeat() {
    runTabulationTest("2013_minneapolis_park_sequential");
  }

  // function: testBottomsUpMultiSeat
  // purpose: tests bottomsUpMultiSeat option
  @Test
  @DisplayName("test bottoms-up multi-seat logic")
  void testBottomsUpMultiSeat() {
    runTabulationTest("2013_minneapolis_park_bottoms_up");
  }

  // function: precinctExample
  // purpose: tests a small election with precincts
  @Test
  @DisplayName("precinct example")
  void precinctExample() {
    runTabulationTest("precinct_example");
  }

  // function: nistTest0
  // purpose: tests skipped first choice
  @Test
  @DisplayName("skipped first choice")
  void nistTest0() {
    runTabulationTest("test_set_0_skipped_first_choice");
  }

  // function: nistTest1
  // purpose: tests exhaust at overvote option
  @Test
  @DisplayName("exhaust at overvote rule")
  void nistTest1() {
    runTabulationTest("test_set_1_exhaust_at_overvote");
  }

  // function: nistTest2
  // purpose: tests overvote skips to next rank option
  @Test
  @DisplayName("overvote skips to next rank")
  void nistTest2() {
    runTabulationTest("test_set_2_overvote_skip_to_next");
  }

  // function: nistTest3
  // purpose: tests skipped choice exhausts option
  @Test
  @DisplayName("skipped choice exhausts option")
  void nistTest3() {
    runTabulationTest("test_set_3_skipped_choice_exhaust");
  }

  // function: nistTest4
  // purpose: tests skipped choice next option
  @Test
  @DisplayName("skipped choice next option")
  void nistTest4() {
    runTabulationTest("test_set_4_skipped_choice_next");
  }

  // function: nistTest5
  // purpose: tests two skipped ranks exhausts option
  @Test
  @DisplayName("two skipped ranks exhausts option")
  void nistTest5() {
    runTabulationTest("test_set_5_two_skipped_choice_exhaust");
  }

  // function: nistTest6
  // purpose: tests duplicate rank exhausts option
  @Test
  @DisplayName("duplicate rank exhausts")
  void nistTest6() {
    runTabulationTest("test_set_6_duplicate_exhaust");
  }

  // function: nistTest7
  // purpose: tests duplicate rank skips to next option
  @Test
  @DisplayName("duplicate rank skips to next option")
  void nistTest7() {
    runTabulationTest("test_set_7_duplicate_skip_to_next");
  }

  // function: multiWinnerWholeThresholdTest
  // purpose: tests multi-seat with a whole number threshold
  @Test
  @DisplayName("multi-seat whole number threshold")
  void multiWinnerWholeThresholdTest() {
    runTabulationTest("test_set_multi_winner_whole_threshold");
  }

  // function: multiWinnerFractionalThresholdTest
  // purpose: tests multi-seat with a fractional threshold
  @Test
  @DisplayName("multi-seat fractional number threshold")
  void multiWinnerFractionalThresholdTest() {
    runTabulationTest("test_set_multi_winner_fractional_threshold");
  }
}