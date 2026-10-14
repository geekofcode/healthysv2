# Administrateur plateforme en base

Le profil administrateur plateforme est attribué dans `accessprofile.platform_administrator`. Les rôles Keycloak ne constituent plus une attribution automatique de ce profil. Les administrateurs plateforme ne reçoivent aucun droit sur les opérations des établissements.

## Premier administrateur

Renseigner temporairement l'UUID `sub` du compte Keycloak contrôlé :

```dotenv
PLATFORM_ADMIN_BOOTSTRAP_SUBJECT=uuid-du-compte-keycloak
```

Après le provisionnement de l'identité propre de ce compte, le bootstrap insère une attribution locale si sa personne est active. Il ne contacte pas Keycloak et ne crée pas d'utilisateur distant. Un sujet différent, une configuration vide ou une personne désactivée ne reçoivent aucune attribution. L'opération est idempotente et ne réactive jamais une attribution `REVOKED`.

Après confirmation du premier accès, retirer la variable du déploiement. Le profil doit toujours être choisi explicitement et satisfaire les conditions contractuelles applicables avant les opérations plateforme.

## Attributions contrôlées supplémentaires

Exécuter uniquement avec un opérateur habilité et le véritable UUID de sujet du compte concerné. La personne doit déjà être provisionnée et active. Ces instructions ne sont pas exécutées automatiquement par la migration :

```sql
BEGIN;
INSERT INTO accessprofile.platform_administrator(person_id,status,grant_source)
SELECT id,'ACTIVE','CONTROLLED_OPERATOR'
FROM identity.person
WHERE keycloak_user_id='00000000-0000-0000-0000-000000000000'::uuid
  AND status='ACTIVE'
ON CONFLICT(person_id) DO NOTHING;
COMMIT;
```

Remplacer le UUID d'exemple et vérifier exactement une attribution correspondante. Une attribution révoquée reste révoquée ; sa réactivation demande une décision opérateur distincte, avec trace du changement.

Pour révoquer :

```sql
UPDATE accessprofile.platform_administrator
SET status='REVOKED',revoked_at=now(),updated_at=now()
WHERE person_id='00000000-0000-0000-0000-000000000000'::uuid;
```

La révocation prend effet lors de la prochaine résolution du profil. Elle ne supprime pas l'identité ni les profils hospitaliers/patients distincts du même compte.

L'identifiant du profil reste déterministe : `UUID.nameUUIDFromBytes(("healthys:platform:" + personId).getBytes(UTF_8))`. Changer la source de l'autorisation vers la base ne change donc pas les identifiants ou signatures associés à ce profil.
