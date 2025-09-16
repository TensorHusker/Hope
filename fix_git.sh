#!/bin/bash
# Script to fix the git repository state

cd /Users/tensorhusker/Git/Hope

# Abort the rebase
git rebase --abort 2>/dev/null || true

# If that didn't work, manually clean up
if [ -d .git/rebase-merge ]; then
    rm -rf .git/rebase-merge
fi

# Reset HEAD to main branch
git checkout main 2>/dev/null || git checkout -f main

# Clean up any untracked files
git clean -fd

# Reset to the last known good commit on main
git reset --hard 70eacbf09b35886c387cee9a06c7407c20d5c69e

# Show status
git status

echo "Repository fixed!"