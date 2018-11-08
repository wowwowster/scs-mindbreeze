<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
 
 
    <xsl:template match="section">
      
        
  
                    <xsl:apply-templates/>
           
        
    </xsl:template>
    <!-- match all additionnal attributes -->
    <xsl:template match="paragraph">
       <p>
           <xsl:apply-templates/>
       </p>
    </xsl:template>
    <xsl:template match="table">
        <table>
            <xsl:apply-templates/>
        </table>
    </xsl:template>
    
    <xsl:template match="td">
        <td>
            <xsl:apply-templates/>
        </td>
    </xsl:template>
    <xsl:template match="tr">
        <tr>
            <xsl:apply-templates/>
        </tr>
    </xsl:template>
    <xsl:template match="li">
        <li>
            <xsl:apply-templates/>
        </li>
    </xsl:template>
    
    
</xsl:stylesheet>

