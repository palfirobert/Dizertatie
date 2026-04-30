DOCUMENT=thesis
LATEX=latex
BIBTEX=bibtex
DVIPS=dvips
#DVIPSOPT=-t letter -Ppdf
DVIPSOPT=-t a4 -Ppdf
PS2PDF=ps2pdf
#PS2PDFOPT=-sPAPERSIZE=letter
PS2PDFOPT=-sPAPERSIZE=a4
PDFVIEWER=acroread

all:	
	$(LATEX) $(DOCUMENT).tex
	$(LATEX) $(DOCUMENT).tex
	$(BIBTEX) $(DOCUMENT) 
	$(LATEX) $(DOCUMENT).tex
	$(LATEX) $(DOCUMENT).tex
	$(DVIPS) $(DVIPSOPT) -f $(DOCUMENT).dvi > $(DOCUMENT).ps
	$(PS2PDF) $(PS2PDFOPT) $(DOCUMENT).ps $(DOCUMENT).pdf
	$(PDFVIEWER) $(DOCUMENT).pdf&

clean:
	rm -f *.ps *.dvi *.log *.toc *.lot *.lof *.aux  *.blg core *~ .log *.backup \#*\# *.pdf *.bbl

zap: clean