# Bitbucket CLI

**Usage:** `bb pr <command> [arguments]`

## Setup

`bb` uses environment variables for your Bitbucket username and application password. Set them as follows:

```sh
export BITBUCKET_USERNAME=<username>          # https://bitbucket.org/account/settings/
export BITBUCKET_APP_PASSWORD=<app_password>  # https://bitbucket.org/account/settings/app-passwords/
```

## Commands

Run `bb` within the root directory of your repository.

```sh
bb pr list                                       # List all pull requests
bb pr info <pull_request_id>                     # Get information about the pull request
bb pr diff <pull_request_id>                     # Get the diff of a pull request
bb pr comment <id> <file_path> <line> <comment>  # Add a comment to a specific line
bb pr comments <pull_request_id>                 # List all comments on a pull request
```

---

### **Notes:**

- **Environment Variables:**
  - Ensure that `BITBUCKET_USERNAME` and `BITBUCKET_APP_PASSWORD` are correctly set to authenticate with Bitbucket.
  
- **Running Commands:**
  - Execute the commands within the root directory of your Git repository to ensure proper functionality.

- **Further Enhancements:**
  - The CLI is designed to be extendable. Future updates may include additional formats like CSV and XML for output.

