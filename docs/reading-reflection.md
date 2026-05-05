#### Building effective agents

A useful part is that Anthropic describes different types of workflows and how to use them.
"Prompt chaining" I've already used in homework. Other more complex ones, like "Parallelization" or "Orchestrator-workers",
I haven't used yet because they require more complex tasks or new projects built from scratch.
Something like "Evaluator-optimizer" I already tried in this repo through the Gemini bot reviewer.
But it was not looped — it was triggered by my prompts.
So, as I understand it, agents are more independent (in comprising with workflows), and they require some level of trust.

#### Steering docs + LinkedIn post

Steering reduces agent context load and is very useful (but I haven't used it yet). It's important not to confuse it with
skills and AGENTS.md. Steering is more architectural — high-level rules — while skills are more technical, with examples.

#### Effective context engineering for AI agents

It's interesting that steering helps improve context engineering. Good steering (along with skills and AGENTS.md) leads to
effective token usage during agent work.
Context engineering is also a skill that needs practice to find the right balance of context for the current task.
Techniques to reduce context pollution are also very useful, and I've already tried the technique of logging results in PLAN.md.
But my PLAN.md is still too large — it would be better to split it, along with AGENTS.md.