import os
import pytest

fitz = pytest.importorskip("fitz")

def test_pdf_parsing():
    test_pdf = os.path.join(os.path.dirname(__file__), '1.pdf')
    
    with open(test_pdf, 'rb') as f:
        file_data = f.read()
        
    doc = fitz.open(stream=file_data, filetype="pdf")
    context_text = "\n".join(page.get_text() for page in doc)
    doc.close()
    
    print(f"✅ PDF Extracted successfully!")
    print(f"Total length: {len(context_text)} characters")
    print("-" * 40)
    print("Preview (first 200 chars):")
    print(context_text[:200].encode('utf-8').decode('utf-8', errors='ignore'))
    print("-" * 40)
    
    assert len(context_text) > 0, "Failed to extract any text from the PDF"

if __name__ == "__main__":
    test_pdf_parsing()
