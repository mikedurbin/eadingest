<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs" version="2.0">
    
    <xsl:template match="holding">
        <container>
            <callNumber><xsl:value-of select="@callNumber" /></callNumber>
            <catalogKey><xsl:value-of select="catalogKey" /></catalogKey>
            <type>box</type>
            <barCode><xsl:value-of select="copy/@barCode" /></barCode>
        </container>
    </xsl:template>
    
    
</xsl:stylesheet>