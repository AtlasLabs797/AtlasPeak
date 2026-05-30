# Artifact integrity + rebuild pattern

Use this when a debugging/fix session touches file-backed outputs and the file itself may be wrong or corrupt.

## When this matters
- `.xlsx` / `.docx` / `.pptx` / `.zip` artifacts
- generated config bundles
- local databases or state files
- any workflow where code looks fixed but the saved artifact may still be invalid

## Pattern
1. **Verify the file opens** with the same library the workflow uses.
2. **Run a structural integrity check** when the format supports it.
3. **Compare artifact contents to source-derived truth** rather than trusting prior rows/cells/state.
4. If the artifact is corrupt or internally inconsistent:
   - restore from backup if available
   - rebuild from canonical inputs
   - re-run verification after replacement

## Concrete session example: Excel Autónomos
- Symptom: `PAGOS.xlsx` failed to reopen with `openpyxl` and raised `BadZipFile: Bad magic number for file header`.
- Structural check: `ZipFile('PAGOS.xlsx').testzip()` reported corruption inside `xl/worksheets/sheet2.xml`.
- Recovery path that worked:
  1. Open the known-good backup workbook.
  2. Fix extraction logic in `process_autonomos.py` first (totals, IBAN, invoice dates).
  3. Re-extract reviewed PDFs as canonical truth.
  4. Rebuild the target month sheets from reviewed PDFs instead of only patching sparse cells.
  5. Save to a temp workbook, reopen it, then replace the production workbook.
  6. Re-run workbook-vs-source comparison until discrepancy count is zero.

## Why rebuild beat patching
Sparse manual cell edits left stale/misplaced rows in month sheets. Rebuilding from reviewed PDFs removed row drift and made verification deterministic.

## Useful checks
```python
from openpyxl import load_workbook
load_workbook('PAGOS.xlsx', data_only=False)
```

```python
from zipfile import ZipFile
with ZipFile('PAGOS.xlsx') as z:
    print(z.testzip())
```

## Takeaway
When debugging data pipelines that write office files, "the code now extracts correctly" is not enough. Verify file integrity, then verify semantic alignment against canonical inputs.
