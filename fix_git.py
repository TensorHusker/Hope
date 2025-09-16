#!/usr/bin/env python3
import subprocess
import os
import shutil

def run_command(cmd):
    """Run a command and return its output"""
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd='/Users/tensorhusker/Git/Hope')
        print(f"Command: {cmd}")
        print(f"Output: {result.stdout}")
        if result.stderr:
            print(f"Error: {result.stderr}")
        return result.returncode == 0
    except Exception as e:
        print(f"Exception running {cmd}: {e}")
        return False

# Change to the project directory
os.chdir('/Users/tensorhusker/Git/Hope')

# Clean up rebase state
rebase_dir = '/Users/tensorhusker/Git/Hope/.git/rebase-merge'
if os.path.exists(rebase_dir):
    print(f"Removing rebase-merge directory...")
    shutil.rmtree(rebase_dir)
    print("Rebase directory removed")

# Try to run git commands
print("\n=== Attempting to fix git repository ===\n")

# Abort any ongoing rebase
run_command("git rebase --abort")

# Check current status
run_command("git status")

# Try to checkout main
run_command("git checkout main")

# Show current branch and commit
run_command("git branch --show-current")
run_command("git rev-parse HEAD")

# Try to pull with rebase
print("\n=== Attempting to sync with remote ===\n")
run_command("git fetch origin")
run_command("git pull origin main --rebase")

print("\n=== Final status ===\n")
run_command("git status")
run_command("git log --oneline -5")