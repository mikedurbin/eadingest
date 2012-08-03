<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs" version="2.0">
    
    <xsl:template match="element()">
        <xsl:copy>
            <xsl:apply-templates select="@*,node()[not(starts-with(name(), 'c0'))]"/>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="attribute()|text()|comment()|processing-instruction()">
        <xsl:copy/>
    </xsl:template>
    
</xsl:stylesheet>