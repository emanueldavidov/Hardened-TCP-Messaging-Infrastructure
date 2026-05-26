import os
import sys
import json
import time
import subprocess
from google import genai
from pydantic import BaseModel

# 1. Define the structural schema we expect back from Gemini
class AutomatedFix(BaseModel):
    root_cause: str
    file_to_fix: str
    fixed_content: str

def analyze_and_get_fix(tool_name, log_content):
    """Communicates with Gemini API to get a structured JSON response containing the fix."""
    client = genai.Client(api_key=os.environ.get("GEMINI_API_KEY"))
    
    prompt = f"""
    You are an autonomous Senior DevSecOps & SRE Expert Agent.
    A CI/CD pipeline step failed for the tool: '{tool_name}' in a Java/Docker project.
    
    Analyze the following error or output log. You must identify exactly which single configuration file, 
    source code file, or manifest broke or contains a vulnerability. 
    Provide your response in the requested JSON schema containing the root cause description, the relative path 
    to the target file, and the entire new corrected content of that file.

    Here is the log snippet/output:
    {log_content}
    """
    
    print(f"🤖 Agent is communicating with Gemini (Structured Mode) for tool: {tool_name}...")
    
    max_retries = 3
    for attempt in range(max_retries):
        try:
            response = client.models.generate_content(
                model='gemini-2.5-flash',
                contents=prompt,
                config={
                    'response_mime_type': 'application/json',
                    'response_schema': AutomatedFix,
                }
            )
            return json.loads(response.text)
        except Exception as e:
            if "503" in str(e) and attempt < max_retries - 1:
                print(f"⚠️ Gemini API temporary overload (503). Retrying in 5 seconds... ({attempt + 1}/{max_retries})")
                time.sleep(5)
                continue
            else:
                raise e

def apply_fix_and_push(failed_tool, fix_data):
    """Applies the code modification locally, creates a new Git branch, pushes it, and opens a PR."""
    file_path = fix_data.get("file_to_fix")
    new_content = fix_data.get("fixed_content")
    root_cause_desc = fix_data.get("root_cause")
    
    if not file_path or not os.path.exists(file_path):
        print(f"❌ Target file '{file_path}' not found or path empty. Skipping Git automation.")
        return

    # Write the correction to the target file
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(new_content)
    print(f"🛠️ Applied AI automated fix locally to: {file_path}")

    # Execute Git and PR creation
    try:
        # Set temporary Git bot profile inside the runner
        subprocess.run(["git", "config", "user.name", "citadel-ai-bot"], check=True)
        subprocess.run(["git", "config", "user.email", "ai-bot@citadel.com"], check=True)
        
        # Create a unique branch name based on timestamp
        branch_name = f"ai-automated-fix-{failed_tool.lower().replace(' ', '-')}-{int(time.time())}"
        subprocess.run(["git", "checkout", "-b", branch_name], check=True)
        
        # Commit the changes
        subprocess.run(["git", "add", file_path], check=True)
        commit_msg = f"🤖 AI Automated Fix for {file_path}\n\nReason: {root_cause_desc}"
        subprocess.run(["git", "commit", "-m", commit_msg], check=True)
        
        # Push the branch to GitHub using the authenticated repository origin
        subprocess.run(["git", "push", "origin", branch_name], check=True)
        print(f"🚀 Pushed new branch '{branch_name}' to GitHub successfully.")
        
        # Open Pull Request using GitHub CLI
        pr_title = f"🤖 AI Fix: Resolved vulnerability/error in {file_path}"
        pr_body = f"### 🤖 Citadel Autonomous DevOps Agent Report\n\n**Detected on Tool:** `{failed_tool}`\n\n#### 🎯 Root Cause Description:\n{root_cause_desc}"
        
        pr_cmd = ["gh", "pr", "create", "--title", pr_title, "--body", pr_body, "--base", "main", "--head", branch_name]
        subprocess.run(pr_cmd, check=True)
        print("🎉 Automation Complete: Pull Request created successfully!")
        
    except subprocess.CalledProcessError as e:
        print(f"❌ Git/PR automation engine failed: {e}")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python pipeline_agent.py <tool_name> <log_file_path>")
        sys.exit(1)
        
    tool = sys.argv[1]
    log_file = sys.argv[2]
    
    if not os.path.exists(log_file):
        print(f"❌ Log file {log_file} not found.")
        sys.exit(1)
        
    with open(log_file, "r", encoding="utf-8", errors="ignore") as f:
        full_log = f.read()
        
    truncated_log = "\n".join(full_log.splitlines()[-150:])
    
    try:
        fix_result = analyze_and_get_fix(tool, truncated_log)
        print("💡 AI Analysis Complete. Root Cause detected:", fix_result.get("root_cause"))
        
        # Write report to markdown file just for artifact logging visibility
        report_md = f"### 🤖 CI/CD Autonomous Agent Report\n\n{fix_result.get('root_cause')}"
        with open("ai_agent_report.md", "w", encoding="utgit push origin mainf-8") as out:
            out.write(report_md)
            
        apply_fix_and_push(tool, fix_result)
        
    except Exception as error:
        print(f"❌ Script failed during processing: {error}")
        sys.exit(1)