###############################################################################
# Copyright (c) 2026 Eurotech and/or its affiliates and others
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# Contributors:
#     Eurotech - initial API and implementation
#
###############################################################################
@datastore
@env_docker_base

Feature: Channel last published tracker
  The tracker writes last_message_on / last_message_id on the channel info docs. Each scenario owns an enabled tracker and stores messages
  through a message store facade wired to it, then reads the fields straight from the channel info doc.

  @setup
  Scenario: Setup test resources (job-engine is required to init DB)
    Given Init Security Context
    And Start Docker environment with resources
      | db         |
      | es         |
      | job-engine |

  Scenario: A flush writes the newest stored message of the channel
    Given I login as user with name "kapua-sys" and password "kapua-password"
    And I select account "kapua-sys"
    And The device "lp-client-1"
    And I set the database to device timestamp indexing
    And System property "datastore.index.prefix" with value ""
    And I set the datastore indexing window to "week"
    And A channel last published tracker with min advance 3600 seconds and flush interval 60000 milliseconds
    When I prepare a number of messages in the specified ranges and remember the list as "LpMessages"
      | clientId    | topic    | count | startDate                | endDate                  |
      | lp-client-1 | lp/topic | 5     | 2025-12-01T10:00:00.000Z | 2025-12-01T10:04:00.000Z |
    And I store the messages from list "LpMessages" through the channel last published tracker
    Then The channel of client "lp-client-1" has no last published message
    When I flush the channel last published tracker
    Then The channel of client "lp-client-1" has the newest stored message as last published message
    And I logout

  Scenario: A message less than N after the written one is not written
    Given I login as user with name "kapua-sys" and password "kapua-password"
    And I select account "kapua-sys"
    And The device "lp-client-2"
    And I set the database to device timestamp indexing
    And System property "datastore.index.prefix" with value ""
    And I set the datastore indexing window to "week"
    And A channel last published tracker with min advance 3600 seconds and flush interval 60000 milliseconds
    When I prepare a number of messages in the specified ranges and remember the list as "LpFirst"
      | clientId    | topic    | count | startDate                | endDate                  |
      | lp-client-2 | lp/topic | 1     | 2025-12-01T10:00:00.000Z | 2025-12-01T10:00:00.000Z |
    And I store the messages from list "LpFirst" through the channel last published tracker
    And I flush the channel last published tracker
    Then The channel of client "lp-client-2" has the newest message of list "LpFirst" as last published message
    # 30 minutes later: below N (1 hour), filtered by the tracker
    When I prepare a number of messages in the specified ranges and remember the list as "LpTooSoon"
      | clientId    | topic    | count | startDate                | endDate                  |
      | lp-client-2 | lp/topic | 1     | 2025-12-01T10:30:00.000Z | 2025-12-01T10:30:00.000Z |
    And I store the messages from list "LpTooSoon" through the channel last published tracker
    And I flush the channel last published tracker
    Then The channel of client "lp-client-2" has the newest message of list "LpFirst" as last published message
    # 2 hours later: at least N, written
    When I prepare a number of messages in the specified ranges and remember the list as "LpLater"
      | clientId    | topic    | count | startDate                | endDate                  |
      | lp-client-2 | lp/topic | 1     | 2025-12-01T12:00:00.000Z | 2025-12-01T12:00:00.000Z |
    And I store the messages from list "LpLater" through the channel last published tracker
    And I flush the channel last published tracker
    Then The channel of client "lp-client-2" has the newest message of list "LpLater" as last published message
    And I logout

  Scenario: A deleted channel info doc is not recreated by the tracker
    Given I login as user with name "kapua-sys" and password "kapua-password"
    And I select account "kapua-sys"
    And The device "lp-client-3"
    And I set the database to device timestamp indexing
    And System property "datastore.index.prefix" with value ""
    And I set the datastore indexing window to "week"
    And A channel last published tracker with min advance 3600 seconds and flush interval 60000 milliseconds
    When I prepare a number of messages in the specified ranges and remember the list as "LpDeleted"
      | clientId    | topic    | count | startDate                | endDate                  |
      | lp-client-3 | lp/topic | 2     | 2025-12-01T10:00:00.000Z | 2025-12-01T10:01:00.000Z |
    And I store the messages from list "LpDeleted" through the channel last published tracker
    # as the TTL cleanup job would do: the doc goes away, the facade cache still has the channel
    And I delete the channel info doc of client "lp-client-3" from the store engine
    And I flush the channel last published tracker
    Then The channel info doc of client "lp-client-3" does not exist
    And I logout

  Scenario: The scheduled flush writes the newest stored message
    Given I login as user with name "kapua-sys" and password "kapua-password"
    And I select account "kapua-sys"
    And The device "lp-client-4"
    And I set the database to device timestamp indexing
    And System property "datastore.index.prefix" with value ""
    And I set the datastore indexing window to "week"
    And A channel last published tracker with min advance 3600 seconds and flush interval 1000 milliseconds
    And I start the channel last published tracker
    When I prepare a number of messages in the specified ranges and remember the list as "LpScheduled"
      | clientId    | topic    | count | startDate                | endDate                  |
      | lp-client-4 | lp/topic | 3     | 2025-12-01T10:00:00.000Z | 2025-12-01T10:02:00.000Z |
    And I store the messages from list "LpScheduled" through the channel last published tracker
    Then The channel of client "lp-client-4" has the newest stored message as last published message within 15 seconds
    And I logout

  Scenario: Over time, the scheduled tracker keeps the field fresh with about one write per N
  A message with the current time is stored every 500 ms for 20 s while the tracker flushes every 2 s with N = 5 s. After each message the
  channel doc is checked: the stored pair is always a real stored message, and it never lags the newest one by more than N + two flush intervals.
  With N = 5 s over 20 s the written values are at least 5 s apart, so the doc gets 3 to 5 tracker writes instead of one per message (about 40).

    Given I login as user with name "kapua-sys" and password "kapua-password"
    And I select account "kapua-sys"
    And The device "lp-client-5"
    And I set the database to device timestamp indexing
    And System property "datastore.index.prefix" with value ""
    And I set the datastore indexing window to "week"
    And A channel last published tracker with min advance 5 seconds and flush interval 2000 milliseconds
    And I start the channel last published tracker
    When I store a message with the current time for client "lp-client-5" on topic "lp/live" through the channel last published tracker every 500 milliseconds for 20 seconds, checking the channel each time
    Then The last published message of client "lp-client-5" is less than the min advance behind the newest stored message within 10 seconds
    And The channel info doc of client "lp-client-5" was written by the tracker between 3 and 5 times
    And I logout

  Scenario: Several channels stored at different times: each one is written at most once per N, independently of the others
  Three channels of one client (lp/a, lp/b, lp/c) get messages in four batches, with a flush after each batch and N = 1 hour.
  A channel is written when its newest message is at least N after the value written for that channel, or when it was never written;
  otherwise the message is held back. Several messages of a channel in the same batch collapse into one write with the newest.

    Given I login as user with name "kapua-sys" and password "kapua-password"
    And I select account "kapua-sys"
    And The device "lp-client-6"
    And I set the database to device timestamp indexing
    And System property "datastore.index.prefix" with value ""
    And I set the datastore indexing window to "week"
    And A channel last published tracker with min advance 3600 seconds and flush interval 60000 milliseconds
    # batch 1: a and b start, c does not exist yet
    When I prepare a number of messages in the specified ranges and remember the list as "LpBatch1"
      | clientId    | topic | count | startDate                | endDate                  |
      | lp-client-6 | lp/a  | 1     | 2025-12-01T10:00:00.000Z | 2025-12-01T10:00:00.000Z |
      | lp-client-6 | lp/b  | 1     | 2025-12-01T10:00:00.000Z | 2025-12-01T10:00:00.000Z |
    And I store the messages from list "LpBatch1" through the channel last published tracker
    And I flush the channel last published tracker
    Then The channel "lp/a" of client "lp-client-6" has the newest message of list "LpBatch1" as last published message
    And The channel "lp/b" of client "lp-client-6" has the newest message of list "LpBatch1" as last published message
    And The channel info doc of channel "lp/c" of client "lp-client-6" does not exist
    # batch 2: a +30 min (held back), b +75 min (written), c first message (written)
    When I prepare a number of messages in the specified ranges and remember the list as "LpBatch2"
      | clientId    | topic | count | startDate                | endDate                  |
      | lp-client-6 | lp/a  | 1     | 2025-12-01T10:30:00.000Z | 2025-12-01T10:30:00.000Z |
      | lp-client-6 | lp/b  | 1     | 2025-12-01T11:15:00.000Z | 2025-12-01T11:15:00.000Z |
      | lp-client-6 | lp/c  | 1     | 2025-12-01T10:45:00.000Z | 2025-12-01T10:45:00.000Z |
    And I store the messages from list "LpBatch2" through the channel last published tracker
    And I flush the channel last published tracker
    Then The channel "lp/a" of client "lp-client-6" has the newest message of list "LpBatch1" as last published message
    And The channel "lp/b" of client "lp-client-6" has the newest message of list "LpBatch2" as last published message
    And The channel "lp/c" of client "lp-client-6" has the newest message of list "LpBatch2" as last published message
    # batch 3: a 65 min after its written 10:00 (written), b +30 min (held back), c +45 min (held back)
    When I prepare a number of messages in the specified ranges and remember the list as "LpBatch3"
      | clientId    | topic | count | startDate                | endDate                  |
      | lp-client-6 | lp/a  | 1     | 2025-12-01T11:05:00.000Z | 2025-12-01T11:05:00.000Z |
      | lp-client-6 | lp/b  | 1     | 2025-12-01T11:45:00.000Z | 2025-12-01T11:45:00.000Z |
      | lp-client-6 | lp/c  | 1     | 2025-12-01T11:30:00.000Z | 2025-12-01T11:30:00.000Z |
    And I store the messages from list "LpBatch3" through the channel last published tracker
    And I flush the channel last published tracker
    Then The channel "lp/a" of client "lp-client-6" has the newest message of list "LpBatch3" as last published message
    And The channel "lp/b" of client "lp-client-6" has the newest message of list "LpBatch2" as last published message
    And The channel "lp/c" of client "lp-client-6" has the newest message of list "LpBatch2" as last published message
    # batch 4: three messages of a (one write, the newest), b one hour after its written 11:15 (written), nothing for c
    When I prepare a number of messages in the specified ranges and remember the list as "LpBatch4"
      | clientId    | topic | count | startDate                | endDate                  |
      | lp-client-6 | lp/a  | 3     | 2025-12-01T12:10:00.000Z | 2025-12-01T12:30:00.000Z |
      | lp-client-6 | lp/b  | 1     | 2025-12-01T12:15:00.000Z | 2025-12-01T12:15:00.000Z |
    And I store the messages from list "LpBatch4" through the channel last published tracker
    And I flush the channel last published tracker
    Then The channel "lp/a" of client "lp-client-6" has the newest message of list "LpBatch4" as last published message
    And The channel "lp/b" of client "lp-client-6" has the newest message of list "LpBatch4" as last published message
    And The channel "lp/c" of client "lp-client-6" has the newest message of list "LpBatch2" as last published message
    # 10 messages in all, 7 writes: a in batches 1, 3, 4; b in batches 1, 2, 4; c in batch 2
    And The channel "lp/a" of client "lp-client-6" was written by the tracker 3 times
    And The channel "lp/b" of client "lp-client-6" was written by the tracker 3 times
    And The channel "lp/c" of client "lp-client-6" was written by the tracker 1 time
    And I logout

  @teardown
  Scenario: Stop full docker environment
    Given Stop Docker environment
    And Clean Locator Instance
