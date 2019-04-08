# Parse ofx files and send to ynab.

## Setup

1. Get a developer token from ynab and set it as the `:ynab-token` property in config.edn.
2. Run `(rb.core/get-budgets)` then copy the id for your budget to the config.edn files `:budget-id` property.
3. Run `(rb.core/get-accounts)` and create an entry for each account you want to push transactions for in the `:accounts` list. 
4. Match the account id to the account number from your bank account.

## Usage with FNB Downloads
1. Download .ofx transaction history files from the account views on the fnb website.
2. Place these `zip` files under `resources/downloads`
3. run `(rb.core/push-transactions-to-ynab)`

## What If I want a different workflow?
You are free to use the various functions in `rb.ofx` and `rb.core` to parse your .ofx or .zip files and send those transactions to ynab

