/*
 * Created by Jonathan Moldover, Louis Eisenberg, and Hylton Edingfield
 * Copyright 2018 Bright Spots
 * Purpose: Handle tie break scenarios based on rules configuration
 * Version: 1.0
 */

package com.rcv;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

class TieBreak {

  // list of candidates who are tied
  private final List<String> tiedCandidates;
  // mode we're using for breaking ties
  private final Tabulator.TieBreakMode tieBreakMode;
  // ordering to use if we're doing permutation-based tie-breaking
  private final ArrayList<String> candidatePermutation;
  // round in which this tiebreak occurred
  private final int round;
  // number of votes the tying candidates received
  private final BigDecimal numVotes;
  // roundTallies: map from round number to map of candidate ID to vote total (for that round)
  // e.g. roundTallies[1] contains a map of candidate IDs to tallies for each candidate in round 1
  private final Map<Integer, Map<String, BigDecimal>> roundTallies;
  // candidate ID selected to lose the tiebreak
  private String loser;
  // reason for the selection
  private String explanation;

  // function: TieBreak
  // purpose: TieBreak constructor stores member data and selects the loser
  // param: tiedCandidates list of all candidate IDs tied at this vote total
  // param: tieBreakMode rule to use for selecting the loser
  // param: round in which this tie occurs
  // param: numVotes tally of votes for tying candidates
  // param: roundTallies map from round number to map of candidate ID to vote total (for that round)
  // return newly constructed TieBreak object
  TieBreak(
      List<String> tiedCandidates,
      Tabulator.TieBreakMode tieBreakMode,
      int round,
      BigDecimal numVotes,
      Map<Integer, Map<String, BigDecimal>> roundTallies,
      ArrayList<String> candidatePermutation
  ) {
    this.tiedCandidates = tiedCandidates;
    this.tieBreakMode = tieBreakMode;
    this.round = round;
    this.numVotes = numVotes;
    this.roundTallies = roundTallies;
    this.candidatePermutation = candidatePermutation;
  }

  // function: nonLosingCandidateDescription
  // purpose: generate a string listing candidate(s) not selected to lose this tiebreak
  // return: string listing candidate(s) not selected to lose
  String nonLosingCandidateDescription() {
    // options: container for non selected candidate IDs
    ArrayList<String> options = new ArrayList<>();
    // contestOptionId indexes over tied candidates
    for (String contestOptionId : tiedCandidates) {
      if (!contestOptionId.equals(loser)) {
        options.add(contestOptionId);
      }
    }
    // container for results
    String nonselected;
    if (options.size() == 1) {
      nonselected = options.get(0);
    } else if (options.size() == 2) {
      // if there are only 2 candidates don't use a comma
      nonselected = options.get(0) + " and " + options.get(1);
    } else {
      // StringBuilder for faster string construction
      StringBuilder stringBuilder = new StringBuilder();
      // i indexes over all candidates
      for (int i = 0; i < options.size() - 1; i++) {
        stringBuilder.append(options.get(i)).append(", ");
      }
      stringBuilder.append("and ").append(options.get(options.size() - 1));
      nonselected = stringBuilder.toString();
    }
    return nonselected;
  }

  String explanation() {
    return explanation;
  }

  // function: breakTie
  // purpose: execute the tiebreak logic given the tiebreak rule in use
  // returns: losing candidate
  String selectLoser() {
    switch (tieBreakMode) {
      case INTERACTIVE:
        loser = doInteractive();
        break;
      case RANDOM:
        loser = doRandom();
        break;
      case GENERATE_PERMUTATION:
      case USE_PERMUTATION_IN_CONFIG:
        loser = doPermutationSelection();
        break;
      default:
        // handle tiebreaks which involve previous round tallies
        // loser: will be set if there is a previous round count loser
        // it will be null if candidates were still tied at first round
        String previousRoundsLoser = doPreviousRounds();
        if (previousRoundsLoser != null) {
          loser = previousRoundsLoser;
        } else if (tieBreakMode == Tabulator.TieBreakMode.PREVIOUS_ROUND_COUNTS_THEN_INTERACTIVE) {
          loser = doInteractive();
        } else {
          loser = doRandom();
        }
    }
    return loser;
  }

  // function: doPermutationSelection
  // purpose: select the loser based on the provided permutation order
  // returns: selected loser
  private String doPermutationSelection() {
    // loser to return
    String selectedCandidate = null;
    // create a set to simplify matching logic
    Set<String> tiedCandidatesSet = new HashSet<>(tiedCandidates);

    // start from end of list and look for a matching candidate
    for (int i = candidatePermutation.size() - 1; i >= 0; i--) {
      // the current candidate we're considering from the permutation
      String candidate = candidatePermutation.get(i);
      if (tiedCandidatesSet.contains(candidate)) {
        selectedCandidate = candidate;
        break;
      }
    }
    explanation = "The losing candidate appeared latest in the tie-breaking permutation list.";
    return selectedCandidate;
  }

  // function doInteraction
  // purpose: interactively select the loser of this tiebreak
  // return: candidateID of the selected loser
  private String doInteractive() {
    System.out.println(
        "Tie in round " + round + " for the following candidateIDs each of whom has " + numVotes
            + " votes:"
    );
    // i: index over tied candidates
    for (int i = 0; i < tiedCandidates.size(); i++) {
      System.out.println((i + 1) + ". " + tiedCandidates.get(i));
    }
    System.out.println(
        "Enter the number corresponding to the candidate who should lose this tiebreaker."
    );
    // the candidate selected to lose
    String selectedCandidate = null;
    while (selectedCandidate == null) {
      // container for user console input
      String userInput = System.console().readLine();
      try {
        // user selected loser parsed to int
        int choice = Integer.parseInt(userInput);
        if (choice >= 1 && choice <= tiedCandidates.size()) {
          explanation = "The losing candidate was supplied by the operator.";
          // Convert from 1-indexed list back to 0-indexed list.
          selectedCandidate = tiedCandidates.get(choice - 1);
        }
      } catch (NumberFormatException e) {
        // if parseInt failed selectedCandidate will be null and we will retry
      }
      if (selectedCandidate == null) {
        System.out.println("Invalid selection. Please try again.");
      }
    }
    return selectedCandidate;
  }

  // function doRandom
  // purpose: randomly select the loser for this tiebreak
  // return: candidateID of the selected loser
  private String doRandom() {
    // random number used for random candidate ID loser
    double randomNormalFloat = Math.random();
    // index of randomly selected candidate
    int randomCandidateIndex = (int) Math.floor(randomNormalFloat * (double) tiedCandidates.size());
    explanation = "The loser was randomly selected.";
    return tiedCandidates.get(randomCandidateIndex);
  }

  // function doPreviousRounds
  // purpose: select loser based on previous round tallies
  // return: candidateID of the selected loser
  private String doPreviousRounds() {
    // stores candidates still in contention while we iterate through previous rounds to determine
    // the loser
    Set<String> candidatesInContention = new HashSet<>(tiedCandidates);
    // the candidate selected to lose
    String loser = null;
    // round indexes from the previous round back to round 1
    for (int round = this.round - 1; round > 0; round--) {
      // map of tally to candidate IDs for round under consideration
      SortedMap<BigDecimal, LinkedList<String>> tallyToCandidates =
          Tabulator.buildTallyToCandidates(
              roundTallies.get(round),
              candidatesInContention,
              false
          );
      // lowest tally for this round
      BigDecimal minVotes = tallyToCandidates.firstKey();
      // candidates receiving the lowest tally
      LinkedList<String> candidatesWithLowestTotal = tallyToCandidates.get(minVotes);
      if (candidatesWithLowestTotal.size() == 1) {
        loser = candidatesWithLowestTotal.getFirst();
        explanation =
            String.format("%s had the fewest votes (%s) in round %d.", loser, minVotes.toString(),
                round);
        break;
      } else {
        // update candidatesInContention and check the previous round
        candidatesInContention = new HashSet<>(candidatesWithLowestTotal);
      }
    }
    return loser;
  }
}
