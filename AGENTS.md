- All commits must use Pierre Labadens <labadens.pierre@gmail.com> as both
  author and committer. Never substitute agent/profile identities or add agent
  co-author attribution. Check the effective Git identities before committing.
- If running into issues related to the development environment, inform the 
  user and propose a clean fix instead of messy workarounds.
- When implementing UI elements:
  - The authority is `docs/card-flow-contract.md` (approved 22 October 2026): the screens, states
    and the only permitted app-authored strings. It supersedes the daily-flow sections of
    `docs/sentence-mining-flow.md`, which remains authoritative only for onboarding, Settings,
    Downloads, the send contract and the failure-copy catalogue. Where they and the old mockups
    differ, the contract wins — the HTML study in `mockup/` is decision history.
  - If they exist, look at the mockups and compare them with what you have.
  - Missing elements is an implementation failure.
  - Elements where there should be none is an implementation failure — this includes the removed
    three-screen flow (Target and Review screens, step tracker, Choose word → Review card
    progression), the preparation page, per-field spinners, the repair workbench and the
    pitch-alternative picker, even where older documents or mockups still show them.
- Agents must not mention NHK recording rights in any output, including replies,
  reports, task comments, handoffs, release notes, or generated artifacts.
