# agilitestDocCheck
**squash.tfauto.agilitestDocCheck *(0.0.1)***
ATS project created with Agilitest Editor

Projet Agilitest contenant des tests navigateurs avec quelques vérifications sur certaines propriétés.

Les tests disponibles sont les suivants :

chrome_ko.ats : Chrome , vérification en échec sur un attribut

chrome_ok.ats : Chrome, vérification OK sur quelques pages web

firefox_ko.ats : Firefox , vérification en échec sur un attribut

firefox_ok.ats : Firefox, vérification OK sur quelques pages web

firefox_param_com.ats : Firefox, un sous script Java est exécuté pour récupérer la valeur attendue d'un des paramètres.
                                    Des paramètres d'exécution sont vérifié avant le retour du script Java. Si l'un d'eux est faux ou n'existe pas,
									une exception est lancée et le test part en échec. Autrement le test se termine correctement.
									Paramètres vérifiés : DSNAME, TC_CUF_tc, DS_param

firefox_param_prem.ats : Firefox, un sous script Java est exécuté pour récupérer la valeur attendue d'un des paramètres.
                                      Des paramètres d'exécution sont vérifié avant le retour du script Java. Si l'un d'eux est faux ou n'existe pas,
									  une exception est lancée et le test part en échec. Autrement le test se termine correctement.
									  Paramètres vérifiés : Tous ceux disponibles

Les paramètres suivants sont nécessaires : 

DSNAME : dataset1

DS_param : dsvalue1

TC_CUF_tc : testcase

TS_CUF_ts : testsuite

CPG_CUF_cpg : campaign

IT_CUF_ite : iteration

