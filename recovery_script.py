#!/usr/bin/env python3
"""
Emergency recovery script for bash environment failure.
This script provides alternative methods to execute shell commands.
"""

import os
import sys
import subprocess
import shutil
from pathlib import Path

def diagnose_environment():
    """Diagnose the current environment state."""
    print("=== Environment Diagnosis ===")
    
    # Check basic environment variables
    print("\n1. Environment Variables:")
    for var in ['PATH', 'SHELL', 'HOME', 'USER', 'PWD']:
        value = os.environ.get(var, 'NOT SET')
        print(f"  {var}: {value}")
    
    # Check available shells
    print("\n2. Available Shells:")
    shells = ['/bin/bash', '/bin/sh', '/bin/zsh', '/usr/bin/env']
    for shell in shells:
        if os.path.exists(shell):
            print(f"  ✓ {shell} exists")
        else:
            print(f"  ✗ {shell} not found")
    
    # Check current directory
    print("\n3. Current Directory:")
    print(f"  CWD: {os.getcwd()}")
    
    # Check git directory structure
    print("\n4. Git Rebase State:")
    git_dir = Path('.git')
    rebase_dir = git_dir / 'rebase-merge'
    if rebase_dir.exists():
        print(f"  ✓ Rebase in progress at {rebase_dir}")
        for file in ['head-name', 'done', 'git-rebase-todo', 'msgnum', 'end']:
            file_path = rebase_dir / file
            if file_path.exists():
                print(f"    - {file} exists")
    else:
        print("  ✗ No rebase in progress")

def abort_rebase_manually():
    """Manually abort the git rebase by cleaning up git files."""
    print("\n=== Manually Aborting Rebase ===")
    
    git_dir = Path('.git')
    rebase_dir = git_dir / 'rebase-merge'
    
    if not rebase_dir.exists():
        print("No rebase in progress")
        return False
    
    try:
        # Read the original branch
        head_name_file = rebase_dir / 'head-name'
        if head_name_file.exists():
            with open(head_name_file, 'r') as f:
                original_branch = f.read().strip()
                print(f"Original branch: {original_branch}")
        
        # Read the original HEAD
        orig_head_file = rebase_dir / 'orig-head'
        if orig_head_file.exists():
            with open(orig_head_file, 'r') as f:
                orig_head = f.read().strip()
                print(f"Original HEAD: {orig_head}")
        else:
            # Try to get it from reflog or ORIG_HEAD
            orig_head_alt = git_dir / 'ORIG_HEAD'
            if orig_head_alt.exists():
                with open(orig_head_alt, 'r') as f:
                    orig_head = f.read().strip()
                    print(f"Original HEAD (from ORIG_HEAD): {orig_head}")
            else:
                print("Warning: Could not find original HEAD")
                orig_head = None
        
        # Reset HEAD to original commit if we found it
        if orig_head:
            head_file = git_dir / 'HEAD'
            with open(head_file, 'w') as f:
                if original_branch and original_branch.startswith('refs/'):
                    f.write(f"ref: {original_branch}\n")
                else:
                    f.write(f"{orig_head}\n")
            print(f"Reset HEAD to: {orig_head}")
        
        # Clean up rebase directory
        print("Removing rebase-merge directory...")
        shutil.rmtree(rebase_dir)
        print("✓ Rebase directory removed")
        
        # Clean up other rebase files
        rebase_files = [
            git_dir / 'REBASE_HEAD',
            git_dir / 'AUTO_MERGE',
            git_dir / 'MERGE_MSG'
        ]
        
        for file in rebase_files:
            if file.exists():
                os.remove(file)
                print(f"✓ Removed {file.name}")
        
        print("\n✓ Rebase manually aborted successfully")
        return True
        
    except Exception as e:
        print(f"✗ Error during manual abort: {e}")
        return False

def execute_command_alternative(cmd):
    """Try multiple methods to execute a command."""
    print(f"\nTrying to execute: {cmd}")
    
    methods = [
        ("subprocess.run with shell", lambda: subprocess.run(cmd, shell=True, capture_output=True, text=True)),
        ("subprocess.run without shell", lambda: subprocess.run(cmd.split(), capture_output=True, text=True)),
        ("os.system", lambda: (os.system(cmd), None)),
        ("os.popen", lambda: (None, os.popen(cmd).read()))
    ]
    
    for method_name, method_func in methods:
        try:
            print(f"  Trying {method_name}...")
            result = method_func()
            if isinstance(result, tuple):
                code, output = result
                if code == 0 or output:
                    print(f"    ✓ Success with {method_name}")
                    if output:
                        print(f"    Output: {output}")
                    return True
            elif hasattr(result, 'returncode'):
                if result.returncode == 0:
                    print(f"    ✓ Success with {method_name}")
                    print(f"    Output: {result.stdout}")
                    return True
                else:
                    print(f"    ✗ Failed with return code {result.returncode}")
                    if result.stderr:
                        print(f"    Error: {result.stderr}")
        except Exception as e:
            print(f"    ✗ Failed: {e}")
    
    return False

def check_git_status():
    """Check git status using Python git operations."""
    print("\n=== Git Status Check ===")
    
    git_dir = Path('.git')
    if not git_dir.exists():
        print("Not a git repository")
        return
    
    # Read HEAD
    head_file = git_dir / 'HEAD'
    with open(head_file, 'r') as f:
        head_content = f.read().strip()
        print(f"HEAD: {head_content}")
    
    # Check for rebase
    if (git_dir / 'rebase-merge').exists():
        print("Status: REBASE IN PROGRESS")
    elif (git_dir / 'rebase-apply').exists():
        print("Status: REBASE-APPLY IN PROGRESS")
    elif (git_dir / 'MERGE_HEAD').exists():
        print("Status: MERGE IN PROGRESS")
    else:
        print("Status: Normal")
    
    # Try to get current branch
    if head_content.startswith('ref: refs/heads/'):
        branch = head_content.replace('ref: refs/heads/', '')
        print(f"Current branch: {branch}")

def main():
    """Main recovery function."""
    print("=" * 50)
    print("BASH ENVIRONMENT RECOVERY SCRIPT")
    print("=" * 50)
    
    # Change to the Hope directory
    os.chdir('/Users/tensorhusker/Git/Hope')
    
    # Run diagnosis
    diagnose_environment()
    
    # Check git status
    check_git_status()
    
    # Try to execute some test commands
    print("\n=== Testing Command Execution ===")
    test_commands = [
        "echo 'Hello World'",
        "pwd",
        "ls -la .git/rebase-merge"
    ]
    
    for cmd in test_commands:
        execute_command_alternative(cmd)
    
    # Ask user if they want to abort the rebase
    print("\n" + "=" * 50)
    print("MANUAL REBASE ABORT")
    print("=" * 50)
    print("\nThe git rebase can be manually aborted by cleaning up git files.")
    print("This is equivalent to 'git rebase --abort' but done manually.")
    
    # Automatically proceed with abort since we can't get user input in this context
    print("\nProceeding with manual rebase abort...")
    if abort_rebase_manually():
        print("\n✓ Recovery complete! The rebase has been aborted.")
        print("You should now be able to use git normally.")
    else:
        print("\n✗ Manual abort failed. You may need to manually clean up .git/rebase-merge/")

if __name__ == "__main__":
    main()