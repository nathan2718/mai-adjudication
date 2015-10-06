MAI (Multi-document Adjudication Interface) is an adjudication tool created by Amber Stubbs (http://amberstubbs.net) for Brandeis University as part of her dissertation research. It is a lightweight program written in Java, with a MySQLite database back end (SQLiteJDBC driver created by David Crawshaw (http://www.zentus.com/sqlitejdbc/)).  MAI was designed to be a partner program to MAE (http://code.google.com/p/mae-annotation/).

MAI takes as input any standoff annotated documents (for best results, the files output by MAE should be used), and it allows for easy adjudication of extent tags, link tags, and non-consuming tags.

The current version of MAI is **0.7.1**. A .zip file containing the .jar file, a user guide, readme.txt and example files is available on the download page. The SVN repository contains the Eclipse workspace for the project, as well as fairly complete Javadocs (more information will be added as MAI progresses).

MAI version 0.7.1 fixes a problem with deleting table entries encountered when sorting the tables based on different columns, and re-introduces the functionality which scrolls the text to the appropriate location when a tag ID is double-clicked.

If you wish to cite MAI in a publication, please use: Amber Stubbs. "MAE and MAI: Lightweight Annotation and Adjudication Tools". In 2011 Proceedings of the Linguistic Annotation Workshop V, Association of Computational Linguistics, Portland, Oregon, July 23-24, 2011.