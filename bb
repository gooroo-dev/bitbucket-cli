#!/bin/bash

###############################################################################
# bb: A Simple Bitbucket CLI Tool
#
# Description:
#   bb is a command-line interface tool designed to interact with Bitbucket
#   repositories. It allows users to manage pull requests, comments, and more
#   directly from the terminal, streamlining the development workflow.
#
# Author:
#   Oleksandr Manzyuk <alexander.manzyuk@gooroo.dev>
#
# License:
#   MIT License. See the LICENSE file in the project root for full license information.
#
# Repository:
#   https://github.com/gooroo-dev/bitbucket-cli
#
# Version:
#   1.0.0
#
###############################################################################

# Ensure required environment variables are set
if [[ -z "$BITBUCKET_USERNAME" || -z "$BITBUCKET_APP_PASSWORD" ]]; then
    echo "Error: BITBUCKET_USERNAME and BITBUCKET_APP_PASSWORD environment variables must be set."
    exit 1
fi

# Check if inside a Git repository
if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
    echo "Error: This command must be run inside a Git repository."
    exit 1
fi

# Function to display usage
usage() {
    echo "Usage: bb pr <command> [arguments]"
    echo ""
    echo "Commands:"
    echo "  list                                 List all pull requests in the repository."
    echo "  info <pull_request_id>               Get information about a specific pull request."
    echo "  diff <pull_request_id>               Get the diff of a pull request."
    echo "  comment <pull_request_id> <file_path> <line> <comment>  Add a comment to a specific line."
    echo "  comments <pull_request_id>           List all comments on a pull request."
    exit 1
}

# Function to make authenticated requests
make_request() {
    local url="$1"
    local method="$2"
    local data="$3"

    # echo "curl -s -u $BITBUCKET_USERNAME:$BITBUCKET_APP_PASSWORD -X $method -H 'Content-Type: application/json' -d \"$data\" $url" >&2

    curl -s -u "$BITBUCKET_USERNAME:$BITBUCKET_APP_PASSWORD" \
         -X "$method" \
         -H "Content-Type: application/json" \
         -d "$data" \
         "$url"
}

# Function to parse Git remote URL and extract workspace and repository
get_workspace_repo() {
    local remote_url
    remote_url=$(git config --get remote.origin.url)

    if [[ -z "$remote_url" ]]; then
        echo "Error: No remote origin found in this Git repository."
        exit 1
    fi

    # Handle SSH URL: git@bitbucket.org:workspace/repo.git
    if [[ "$remote_url" =~ git@bitbucket\.org:(.+)/(.+)\.git ]]; then
        WORKSPACE="${BASH_REMATCH[1]}"
        REPO="${BASH_REMATCH[2]}"
    # Handle HTTPS URL: https://bitbucket.org/workspace/repo.git
    elif [[ "$remote_url" =~ https://bitbucket\.org/([^/]+)/([^/]+)(\.git)? ]]; then
        WORKSPACE="${BASH_REMATCH[1]}"
        REPO="${BASH_REMATCH[2]}"
    else
        echo "Error: Unsupported remote URL format. Please ensure the remote origin points to Bitbucket."
        exit 1
    fi
}

# Get workspace and repository from Git remote
get_workspace_repo

# Construct base API URL
API_BASE_URL="https://api.bitbucket.org/2.0/repositories/${WORKSPACE}/${REPO}"

# Ensure at least two arguments are provided
if [ $# -lt 2 ]; then
    usage
fi

# Parse main command
COMMAND=$1
SUBCOMMAND=$2

case $COMMAND in
    pr)
        case $SUBCOMMAND in
            list)
                if [ $# -ne 2 ]; then
                    echo "Usage: bb pr list"
                    exit 1
                fi
                # Bitbucket API: GET /2.0/repositories/{workspace}/{repo}/pullrequests
                API_URL="${API_BASE_URL}/pullrequests?state=OPEN"
                RESPONSE=$(make_request "$API_URL" "GET")
                echo "$RESPONSE" | jq
                ;;

            info)
                if [ $# -ne 3 ]; then
                    echo "Usage: bb pr info <pull_request_id>"
                    exit 1
                fi
                PR_ID=$3
                # Bitbucket API: GET /2.0/repositories/{workspace}/{repo}/pullrequests/{pull_request_id}
                API_URL="${API_BASE_URL}/pullrequests/${PR_ID}"
                RESPONSE=$(make_request "$API_URL" "GET")
                echo "$RESPONSE" | jq
                ;;

            diff)
                if [ $# -ne 3 ]; then
                    echo "Usage: bb [-v] pr diff <pull_request_id>" >&2
                    exit 1
                fi
                PR_ID=$3

                # Fetch PR info to get the diff URL
                PR_INFO_URL="${API_BASE_URL}/pullrequests/${PR_ID}"
                PR_INFO_RESPONSE=$(make_request "$PR_INFO_URL" "GET")

                # Extract the diff URL using jq
                DIFF_URL=$(echo "$PR_INFO_RESPONSE" | jq -r '.links.diff.href')

                # Check if DIFF_URL is not null or empty
                if [[ -z "$DIFF_URL" || "$DIFF_URL" == "null" ]]; then
                    echo "Error: Diff URL not found for pull request ID: $PR_ID" >&2
                    exit 1
                fi

                # Fetch the diff using the extracted URL
                # The diff URL might require specific headers; set Accept to text/plain
                DIFF_RESPONSE=$(make_request "$DIFF_URL" "GET" "" "Accept: text/plain")

                # Check if DIFF_RESPONSE is not empty
                if [[ -z "$DIFF_RESPONSE" ]]; then
                    echo "No diff available for pull request ID: $PR_ID" >&2
                    exit 1
                fi

                echo "$DIFF_RESPONSE"
                ;;
            comment)
                if [ $# -ne 6 ]; then
                    echo "Usage: bb pr comment <pull_request_id> <file_path> <line> <comment>"
                    exit 1
                fi
                PR_ID=$3
                FILE_PATH=$4
                LINE=$5
                COMMENT=$6
                # Bitbucket API: POST /2.0/repositories/{workspace}/{repo}/pullrequests/{pull_request_id}/comments
                COMMENTS_URL="${API_BASE_URL}/pullrequests/${PR_ID}/comments"
                DATA=$(jq -n --arg path "$FILE_PATH" --argjson line "$LINE" --arg text "$COMMENT" \
                    '{content: {raw: $text}, inline: {path: $path, to: $line, from: $line}}')
                RESPONSE=$(make_request "$COMMENTS_URL" "POST" "$DATA")
                echo "$RESPONSE" | jq
                ;;

            comments)
                if [ $# -ne 3 ]; then
                    echo "Usage: bb pr comments <pull_request_id>"
                    exit 1
                fi
                PR_ID=$3
                # Bitbucket API: GET /2.0/repositories/{workspace}/{repo}/pullrequests/{pull_request_id}/comments
                COMMENTS_URL="${API_BASE_URL}/pullrequests/${PR_ID}/comments"
                RESPONSE=$(make_request "$COMMENTS_URL" "GET")
                echo "$RESPONSE" | jq
                ;;

            *)
                echo "Unknown subcommand for pr: $SUBCOMMAND"
                usage
                ;;
        esac
        ;;

    *)
        echo "Unknown command: $COMMAND"
        usage
        ;;
esac
