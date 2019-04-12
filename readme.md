# Download ofx files from FNB and send to YNAB

## Setup

1. YNAB
	1. Get a developer token from ynab and set it as the `:ynab-token` property in config.edn.
	2. Run `(rb.core/get-budgets)` then copy the id for your budget to the config.edn files `:budget-id` property.
	3. Run `(rb.core/get-accounts)` and create an entry for each account you want to push transactions for in the `:accounts` list. 
	4. Match the account id to the account number from your bank account.
2. FNB
	1. Setup Chrome Driver (see below) 
	2. Create a ReadOnly Login on FNB and add the login details to your config.edn
3. Replace the code in `rb.core/-main` to point to your config
4. Run your code (either `clj -m rb.core` or from the repl

## Auto downloading from fnb
[etaoin](https://github.com/igrishaev/etaoin) is used for browser automation.

Install Chrome Driver and make sure your chrome version matches. `brew cask install chromedriver`.

If you want to use a different browser simply swap out the `driver` in `rb/fnb.clj` and see the etaoin website for install details.
