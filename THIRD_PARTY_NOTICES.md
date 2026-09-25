# Third-Party Notices

## Fonts

- **Poppins** (`app/src/main/res/font/poppins_semibold.ttf`, `poppins_bold.ttf`)
  - Source: Google Fonts repository, `ofl/poppins`
  - License: SIL Open Font License 1.1

- **Inter** (`app/src/main/res/font/inter_variable.ttf`)
  - Source: Google Fonts repository, `ofl/inter`
  - License: SIL Open Font License 1.1

The fonts are bundled locally so typography works offline and does not depend on runtime font downloads.

## Data

- **SecLists — 10k most common passwords** (`app/src/main/res/raw/common_passwords.txt`)
  - Source: `danielmiessler/SecLists`, `Passwords/Common-Credentials/10k-most-common.txt`
  - Filtered to entries of 8+ characters, lowercased and deduplicated.
  - License: MIT
  - Used only on-device to reject weak backup passphrases; never transmitted.
