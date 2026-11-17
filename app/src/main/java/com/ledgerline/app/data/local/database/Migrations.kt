package com.ledgerline.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema changes keep existing rows. Do not enable destructive fallback.
 */
object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE recurring_payments ADD COLUMN maxOccurrences INTEGER")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recurring_occurrences` (
                `id` TEXT NOT NULL,
                `recurringPaymentId` TEXT NOT NULL,
                `occurrenceOn` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `amountMinor` INTEGER,
                `name` TEXT,
                `paidTransactionId` TEXT,
                `note` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`recurringPaymentId`) REFERENCES `recurring_payments`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringPaymentId_occurrenceOn` ON `recurring_occurrences` (`recurringPaymentId`, `occurrenceOn`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringPaymentId` ON `recurring_occurrences` (`recurringPaymentId`)",
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE user_settings ADD COLUMN cycleType TEXT NOT NULL DEFAULT 'MONTHLY'")
            db.execSQL("ALTER TABLE user_settings ADD COLUMN cycleAnchor TEXT NOT NULL DEFAULT '2026-01-20'")
            db.execSQL("ALTER TABLE user_settings ADD COLUMN cycleStartDay INTEGER NOT NULL DEFAULT 20")
            db.execSQL("ALTER TABLE user_settings ADD COLUMN cycleSecondDay INTEGER NOT NULL DEFAULT 5")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE debts ADD COLUMN debtType TEXT NOT NULL DEFAULT 'OTHER'")
            db.execSQL("ALTER TABLE debts ADD COLUMN rateType TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE debts ADD COLUMN paymentAccountId TEXT")
            db.execSQL("ALTER TABLE debts ADD COLUMN statementCloseDay INTEGER")
            db.execSQL("ALTER TABLE debts ADD COLUMN originationOn TEXT")
            db.execSQL("ALTER TABLE debts ADD COLUMN maturityOn TEXT")
            db.execSQL("ALTER TABLE debts ADD COLUMN remainingTermMonths INTEGER")
            db.execSQL("ALTER TABLE debts ADD COLUMN feeMinor INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE debts ADD COLUMN rateUpdatedOn TEXT")
            db.execSQL("ALTER TABLE debts ADD COLUMN accruedInterestMinor INTEGER")
            db.execSQL("ALTER TABLE debts ADD COLUMN showInCalendar INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE debts ADD COLUMN aprKnown INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE debts ADD COLUMN minimumKnown INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE debts ADD COLUMN purchaseAprBasisPoints INTEGER")
            db.execSQL("ALTER TABLE debts ADD COLUMN balanceTransferAprBasisPoints INTEGER")
            db.execSQL("ALTER TABLE debts ADD COLUMN cashAdvanceAprBasisPoints INTEGER")
            db.execSQL("ALTER TABLE debts ADD COLUMN graceOnPurchases INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE debts ADD COLUMN dayCountBasis INTEGER NOT NULL DEFAULT 365")
            db.execSQL("ALTER TABLE debts ADD COLUMN recurringPaymentId TEXT")
            db.execSQL("ALTER TABLE debts ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `debt_month_plans` (
                `id` TEXT NOT NULL,
                `debtId` TEXT NOT NULL,
                `yearMonth` TEXT NOT NULL,
                `plannedPaymentMinor` INTEGER NOT NULL,
                `dueOn` TEXT,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`debtId`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_debt_month_plans_debtId_yearMonth` ON `debt_month_plans` (`debtId`, `yearMonth`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `debt_rate_changes` (
                `id` TEXT NOT NULL,
                `debtId` TEXT NOT NULL,
                `effectiveOn` TEXT NOT NULL,
                `aprBasisPoints` INTEGER NOT NULL,
                `note` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`debtId`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_rate_changes_debtId` ON `debt_rate_changes` (`debtId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `debt_settlements` (
                `id` TEXT NOT NULL,
                `debtId` TEXT NOT NULL,
                `yearMonth` TEXT NOT NULL,
                `paidOn` TEXT NOT NULL,
                `amountMinor` INTEGER NOT NULL,
                `transactionId` TEXT NOT NULL,
                `linkId` TEXT,
                `interestMinor` INTEGER NOT NULL,
                `principalMinor` INTEGER NOT NULL,
                `feeMinor` INTEGER NOT NULL,
                `splitKnown` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`debtId`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_debt_settlements_debtId_yearMonth` ON `debt_settlements` (`debtId`, `yearMonth`)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_settlements_linkId` ON `debt_settlements` (`linkId`)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN unplanned INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `expense_notes` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `amountMinor` INTEGER,
                `dueOn` TEXT,
                `note` TEXT NOT NULL,
                `completed` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }

    /** Deleted accounts stay as a dated record so history keeps a valid account reference. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN deletedAtEpochMillis INTEGER")
        }
    }

    /** Adds the captured-spend inbox. Existing tables are not touched. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `captured_spends` (
                `id` TEXT NOT NULL,
                `packageName` TEXT NOT NULL,
                `sourceLabel` TEXT NOT NULL,
                `channel` TEXT NOT NULL,
                `amountMinor` INTEGER NOT NULL,
                `currency` TEXT NOT NULL,
                `merchant` TEXT NOT NULL,
                `lastFour` TEXT,
                `direction` TEXT NOT NULL,
                `refund` INTEGER NOT NULL,
                `occurredOn` TEXT NOT NULL,
                `capturedAtEpochMillis` INTEGER NOT NULL,
                `preview` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `transactionId` TEXT,
                `fingerprint` TEXT NOT NULL,
                PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_captured_spends_fingerprint` ON `captured_spends` (`fingerprint`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_captured_spends_status` ON `captured_spends` (`status`)")
        }
    }

    /** Adds autopay to bills. Existing bills keep being marked paid by hand (the column starts empty). */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE recurring_payments ADD COLUMN autopaySince TEXT")
        }
    }

    /** Adds savings goals and the money put toward them. New tables only; nothing existing changes. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `savings_goals` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `targetMinor` INTEGER NOT NULL, " +
                    "`currency` TEXT NOT NULL, `targetDate` TEXT, `accountId` TEXT, `useAccountBalance` INTEGER NOT NULL, " +
                    "`monthlyMinor` INTEGER, `createdOn` TEXT NOT NULL, `archived` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `goal_contributions` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, " +
                    "`onDate` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`)")
        }
    }

    /** Home groups ("Monthly", "Subscriptions") and which bills and envelopes sit in each. Two new tables only. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `home_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `home_group_items` (`itemKey` TEXT NOT NULL, `groupId` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`itemKey`))")
        }
    }

    /** Planned spends: a paid flag (existing rows are paid) and the day a spend was planned for. Two new columns. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `paid` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `plannedOn` TEXT")
        }
    }

    /** Per-cycle amounts and the start-of-cycle checklist: two new tables, nothing existing changes. */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `cycle_amounts` (`cycleStart` TEXT NOT NULL, `itemKey` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`cycleStart`, `itemKey`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `cycle_reviews` (`cycleStart` TEXT NOT NULL, `reviewedAccounts` TEXT NOT NULL, `balancesChecked` INTEGER NOT NULL, `completedAtEpochMillis` INTEGER, PRIMARY KEY(`cycleStart`))")
        }
    }

    /** Spending items can start and end: three nullable columns on budgets, nothing else changes. */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `budgets` ADD COLUMN `startOn` TEXT")
            db.execSQL("ALTER TABLE `budgets` ADD COLUMN `endOn` TEXT")
            db.execSQL("ALTER TABLE `budgets` ADD COLUMN `maxCycles` INTEGER")
        }
    }

    /** A goal can record what became of its money: two nullable columns on savings_goals, nothing else changes. */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `savings_goals` ADD COLUMN `outcome` TEXT")
            db.execSQL("ALTER TABLE `savings_goals` ADD COLUMN `outcomeOn` TEXT")
        }
    }

    /** Goal entries say what they were (saved, spent on the goal, used elsewhere) and link a spend: two columns. */
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `goal_contributions` ADD COLUMN `kind` TEXT")
            db.execSQL("ALTER TABLE `goal_contributions` ADD COLUMN `transactionId` TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
    )
}
